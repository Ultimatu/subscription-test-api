package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.client.UpdateClientCommand;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService implements ClientUseCases {

	private final ClientRepository clientRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final AuditTrail auditTrail;

	public ClientService(ClientRepository clientRepository, SubscriptionRepository subscriptionRepository,
			AuditTrail auditTrail) {
		this.clientRepository = clientRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.auditTrail = auditTrail;
	}

	@Override
	public Client create(CreateClientCommand command) {
		Client client = Client.create(command.firstName(), command.lastName(), command.email(),
				command.phoneNumberClient(), command.city());
		if (clientRepository.existsByEmail(client.getEmail())) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_EMAIL_ALREADY_USED,
					"Un client existe déjà avec l'email " + client.getEmail());
		}
		Client saved = clientRepository.save(client);
		auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_CREATED, AuditResource.CLIENT, saved.getId(),
				saved.getId(), "Client " + fullName(saved) + " créé",
				Map.of("email", saved.getEmail(), "city", saved.getCity())));
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Client> search(ClientSearchCriteria criteria, Pageable pageable) {
		return clientRepository.search(criteria, pageable);
	}

	/** Aucune transaction englobante : chaque lot est lu dans sa propre transaction, courte. */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public BatchedExport<Client> export(ClientSearchCriteria criteria) {
		BatchedExport<Client> batches = KeysetExport.of(
				(afterId, size) -> clientRepository.findBatch(criteria, afterId, size), Client::getId);
		return action -> {
			long rows = batches.forEachBatch(action);
			auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENTS_EXPORTED, AuditResource.CLIENT, null, null,
					"Export de " + rows + " client(s)", Map.of("rows", rows, "filters",
							KeysetExport.usedFilters("city", criteria.city(), "status", criteria.status(), "search",
									criteria.search()))));
			return rows;
		};
	}

	@Override
	@Transactional(readOnly = true)
	public Client get(Long id) {
		return findClient(id);
	}

	@Override
	public Client update(Long id, UpdateClientCommand command) {
		Client client = findClient(id);
		Client before = Client.restore(client.getId(), client.getFirstName(), client.getLastName(), client.getEmail(),
				client.getPhoneNumberClient(), client.getCity(), client.getStatus(), client.getAudit());
		client.update(command.firstName(), command.lastName(), command.phoneNumberClient(), command.city(),
				command.status());
		Client saved = clientRepository.save(client);
		Changes changes = new Changes()
				.track("firstName", before.getFirstName(), saved.getFirstName())
				.track("lastName", before.getLastName(), saved.getLastName())
				.track("phoneNumberClient", before.getPhoneNumberClient(), saved.getPhoneNumberClient())
				.track("city", before.getCity(), saved.getCity())
				.track("status", before.getStatus(), saved.getStatus());
		if (!changes.isEmpty()) {
			auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_UPDATED, AuditResource.CLIENT, saved.getId(),
					saved.getId(), "Client " + fullName(saved) + " modifié", changes.asMap()));
		}
		return saved;
	}

	private static String fullName(Client client) {
		return client.getFirstName() + " " + client.getLastName();
	}

	@Override
	@Transactional(readOnly = true)
	public ClientSummary summarize(Long id) {
		findClient(id);
		Map<SubscriptionStatus, Long> subscriptionsByStatus = new EnumMap<>(SubscriptionStatus.class);
		for (SubscriptionStatus status : SubscriptionStatus.values()) {
			subscriptionsByStatus.put(status, 0L);
		}
		subscriptionsByStatus.putAll(subscriptionRepository.countByStatusForClient(id));
		// Deux décimales comme tout montant, même quand il n'y a rien à additionner
		BigDecimal totalMonthlyRevenue = subscriptionRepository
				.sumMonthlyPriceForClient(id, SubscriptionStatus.ACTIVE)
				.setScale(2, RoundingMode.UNNECESSARY);
		return new ClientSummary(id, subscriptionsByStatus, totalMonthlyRevenue);
	}

	private Client findClient(Long id) {
		return clientRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", id));
	}

}
