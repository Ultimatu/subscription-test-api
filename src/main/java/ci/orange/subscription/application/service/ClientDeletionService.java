package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.client.ClientDeletionUseCases;
import ci.orange.subscription.application.port.in.client.DeletedClient;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientDeletionService implements ClientDeletionUseCases {

	/** Un client qui a une souscription dans l'un de ces statuts ne peut pas être supprimé (règle 6). */
	private static final Set<SubscriptionStatus> DELETION_BLOCKING_STATUSES = EnumSet.of(SubscriptionStatus.PENDING,
			SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ClientRepository clientRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final DeletionPolicy deletionPolicy;
	private final AuditTrail auditTrail;
	private final Clock clock;

	public ClientDeletionService(ClientRepository clientRepository, SubscriptionRepository subscriptionRepository,
			DeletionPolicy deletionPolicy, AuditTrail auditTrail, Clock clock) {
		this.clientRepository = clientRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.deletionPolicy = deletionPolicy;
		this.auditTrail = auditTrail;
		this.clock = clock;
	}

	/** Les souscriptions résiliées sont supprimées avec le client ; tous restent en base pour la traçabilité. */
	@Override
	public void delete(Long id) {
		Client client = lockClient(id);
		ensureNoOngoingSubscription(id);
		if (deletionPolicy.softDelete() && subscriptionRepository.existsAnyForClient(id)) {
			subscriptionRepository.deleteAllByClientId(id);
			clientRepository.delete(client);
			auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_DELETED, AuditResource.CLIENT, id, id,
					"Client " + client.getFirstName() + " " + client.getLastName()
							+ " supprimé, conservé jusqu'à sa purge",
					Map.of("permanent", false)));
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
			lockClient(id);
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

	/**
	 * Verrouille le client jusqu'à la fin de la transaction, comme toute écriture sur ses souscriptions
	 * (ADR-0014) : une souscription créée au même moment ne peut pas survivre à la suppression de son
	 * client (règle 6).
	 */
	private Client lockClient(Long id) {
		return clientRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", id));
	}

}
