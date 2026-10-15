package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.client.DeletedClient;
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
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService implements ClientUseCases {

	/** Un client qui a une souscription dans l'un de ces statuts ne peut pas être supprimé (règle 6). */
	private static final Set<SubscriptionStatus> DELETION_BLOCKING_STATUSES = EnumSet.of(SubscriptionStatus.PENDING,
			SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ClientRepository clientRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final DeletionPolicy deletionPolicy;
	private final AuditTrail auditTrail;
	private final Clock clock;

	public ClientService(ClientRepository clientRepository, SubscriptionRepository subscriptionRepository,
			DeletionPolicy deletionPolicy, AuditTrail auditTrail, Clock clock) {
		this.clientRepository = clientRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.deletionPolicy = deletionPolicy;
		this.auditTrail = auditTrail;
		this.clock = clock;
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

	/** Les souscriptions résiliées sont supprimées avec le client ; tous restent en base pour la traçabilité. */
	@Override
	public void delete(Long id) {
		Client client = findClient(id);
		ensureNoOngoingSubscription(id);
		if (deletionPolicy.softDelete() && subscriptionRepository.existsAnyForClient(id)) {
			subscriptionRepository.deleteAllByClientId(id);
			clientRepository.delete(client);
			auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_DELETED, AuditResource.CLIENT, id, id,
					"Client " + fullName(client) + " supprimé, conservé jusqu'à sa purge", Map.of("permanent", false)));
		}
		else {
			// Un client qui n'a jamais souscrit n'a rien à conserver
			erase(id, AuditAction.CLIENT_DELETED, "Client n° " + id + " supprimé définitivement", null);
		}
	}

	@Override
	public void purge(Long id) {
		if (!deletionPolicy.hardDelete()) {
			throw new BusinessRuleViolationException(DomainErrorCode.HARD_DELETE_DISABLED,
					"La suppression définitive est désactivée par la configuration (app.deletion.hard-delete)");
		}
		Optional<Instant> deletedAt = clientRepository.findDeletionDate(id);
		if (deletedAt.isPresent()) {
			ensureRetentionPeriodElapsed(deletedAt.get());
		}
		else {
			findClient(id);
			ensureNoOngoingSubscription(id);
			if (deletionPolicy.retentionEnabled() && subscriptionRepository.existsAnyForClient(id)) {
				throw new BusinessRuleViolationException(DomainErrorCode.RETENTION_PERIOD_NOT_ELAPSED,
						"Le client a un historique à conserver : il doit d'abord être supprimé, puis pourra être purgé "
								+ "à l'issue du délai de conservation (" + deletionPolicy.retentionPeriod() + ")");
			}
		}
		erase(id, AuditAction.CLIENT_PURGED, "Client n° " + id + " purgé", null);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<DeletedClient> listDeleted(Pageable pageable) {
		return clientRepository.findDeleted(pageable)
				.map(deleted -> new DeletedClient(deleted.id(), deleted.firstName(), deleted.lastName(),
						deleted.email(), deleted.deletedAt(), deleted.deletedBy(),
						deletionPolicy.purgeableFrom(deleted.deletedAt(), clock.getZone())));
	}

	/** La « routine » de purge : rien ne reste au-delà du délai de conservation (loi n° 2013-450, art. 16). */
	@Override
	public int purgeExpired() {
		if (!deletionPolicy.hardDelete() || !deletionPolicy.retentionEnabled()) {
			return 0;
		}
		List<Long> expired = clientRepository
				.findDeletedBefore(deletionPolicy.purgeableIfDeletedBefore(clock.instant(), clock.getZone()));
		expired.forEach(id -> erase(id, AuditAction.CLIENT_PURGED,
				"Client n° " + id + " purgé à l'expiration du délai de conservation", "system"));
		return expired.size();
	}

	/**
	 * Suppression définitive : les événements du journal qui le concernent contiennent ses données
	 * personnelles et sont effacés avec lui (loi n° 2013-450, art. 36) ; seule reste une trace anonyme.
	 */
	private void erase(Long id, AuditAction action, String summary, String actor) {
		clientRepository.deletePermanently(id);
		auditTrail.eraseClient(id);
		auditTrail.record(new AuditTrail.Entry(action, AuditResource.CLIENT, id.toString(), null, summary,
				Map.of("permanent", true), actor));
	}

	private static String fullName(Client client) {
		return client.getFirstName() + " " + client.getLastName();
	}

	private void ensureNoOngoingSubscription(Long id) {
		if (subscriptionRepository.existsByClientIdAndStatusIn(id, DELETION_BLOCKING_STATUSES)) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_HAS_ONGOING_SUBSCRIPTIONS,
					"Le client a des souscriptions PENDING, ACTIVE ou SUSPENDED : elles doivent être résiliées avant sa suppression");
		}
	}

	private void ensureRetentionPeriodElapsed(Instant deletedAt) {
		Instant purgeableFrom = deletionPolicy.purgeableFrom(deletedAt, clock.getZone());
		if (clock.instant().isBefore(purgeableFrom)) {
			throw new BusinessRuleViolationException(DomainErrorCode.RETENTION_PERIOD_NOT_ELAPSED,
					"Délai de conservation non écoulé : le client pourra être purgé à partir du "
							+ DAY.format(purgeableFrom.atZone(clock.getZone())));
		}
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
