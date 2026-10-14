package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.client.UpdateClientCommand;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService implements ClientUseCases {

	/** A client holding a subscription in one of these statuses cannot be deleted (rule 6). */
	private static final Set<SubscriptionStatus> DELETION_BLOCKING_STATUSES = EnumSet.of(SubscriptionStatus.PENDING,
			SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED);

	private final ClientRepository clientRepository;
	private final SubscriptionRepository subscriptionRepository;

	public ClientService(ClientRepository clientRepository, SubscriptionRepository subscriptionRepository) {
		this.clientRepository = clientRepository;
		this.subscriptionRepository = subscriptionRepository;
	}

	@Override
	public Client create(CreateClientCommand command) {
		Client client = Client.create(command.firstName(), command.lastName(), command.email(),
				command.phoneNumberClient(), command.city());
		if (clientRepository.existsByEmail(client.getEmail())) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_EMAIL_ALREADY_USED,
					"Un client existe déjà avec l'email " + client.getEmail());
		}
		return clientRepository.save(client);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Client> search(ClientSearchCriteria criteria, Pageable pageable) {
		return clientRepository.search(criteria, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public Client get(Long id) {
		return findClient(id);
	}

	@Override
	public Client update(Long id, UpdateClientCommand command) {
		Client client = findClient(id);
		client.update(command.firstName(), command.lastName(), command.phoneNumberClient(), command.city(),
				command.status());
		return clientRepository.save(client);
	}

	/** Terminated subscriptions are deleted along with the client; both stay in storage for traceability. */
	@Override
	public void delete(Long id) {
		Client client = findClient(id);
		if (subscriptionRepository.existsByClientIdAndStatusIn(id, DELETION_BLOCKING_STATUSES)) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_HAS_ONGOING_SUBSCRIPTIONS,
					"Le client a des souscriptions PENDING, ACTIVE ou SUSPENDED : elles doivent être résiliées avant sa suppression");
		}
		subscriptionRepository.deleteAllByClientId(id);
		clientRepository.delete(client);
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
		BigDecimal totalMonthlyRevenue = subscriptionRepository.sumMonthlyPriceForClient(id,
				SubscriptionStatus.ACTIVE);
		return new ClientSummary(id, subscriptionsByStatus, totalMonthlyRevenue);
	}

	private Client findClient(Long id) {
		return clientRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", id));
	}

}
