package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.in.subscription.SubscriptionDetails;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionNumberSequences;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionEligibility;
import ci.orange.subscription.domain.subscription.SubscriptionNumbering;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import ci.orange.subscription.domain.technology.Technology;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionService implements SubscriptionUseCases {

	private final SubscriptionRepository subscriptionRepository;
	private final SubscriptionQueries subscriptionQueries;
	private final ClientRepository clientRepository;
	private final OfferRepository offerRepository;
	private final TechnologyRepository technologyRepository;
	private final SubscriptionNumberSequences numberSequences;
	private final NotificationOutbox notificationOutbox;
	private final DeletionPolicy deletionPolicy;
	private final AuditTrail auditTrail;
	private final Clock clock;

	public SubscriptionService(SubscriptionRepository subscriptionRepository, SubscriptionQueries subscriptionQueries,
			ClientRepository clientRepository, OfferRepository offerRepository,
			TechnologyRepository technologyRepository, SubscriptionNumberSequences numberSequences,
			NotificationOutbox notificationOutbox, DeletionPolicy deletionPolicy, AuditTrail auditTrail, Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.subscriptionQueries = subscriptionQueries;
		this.clientRepository = clientRepository;
		this.offerRepository = offerRepository;
		this.technologyRepository = technologyRepository;
		this.numberSequences = numberSequences;
		this.notificationOutbox = notificationOutbox;
		this.deletionPolicy = deletionPolicy;
		this.auditTrail = auditTrail;
		this.clock = clock;
	}

	/**
	 * D'abord les données invalides, puis les références inconnues, puis les règles 12 à 14 dans
	 * l'ordre imposé. Le client reste verrouillé jusqu'à la fin de la transaction, pour qu'une
	 * requête simultanée sur le même client ne puisse pas passer les mêmes vérifications en même
	 * temps. Les numéros ne sont tirés qu'une fois toutes les vérifications passées.
	 */
	@Override
	public Subscription create(CreateSubscriptionCommand command) {
		LocalDate today = LocalDate.now(clock);
		Subscription.ensureStartDateNotInPast(command.startDate(), today);

		Client client = clientRepository.findByIdForUpdate(command.clientId())
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client",
						command.clientId()));
		Offer offer = offerRepository.findById(command.offerId())
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.OFFER_NOT_FOUND, "Offre",
						command.offerId()));

		Technology technology = technologyRepository.findByCode(offer.getTechnology())
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.TECHNOLOGY_NOT_FOUND, "Technologie",
						offer.getTechnology()));

		SubscriptionEligibility.check(client, offer, technology,
				subscriptionRepository.existsByClientIdAndStatusIn(client.getId(),
						EnumSet.of(SubscriptionStatus.SUSPENDED)),
				subscriptionRepository.existsOngoingForClientAndTechnology(client.getId(), offer.getTechnology()));

		String reference = SubscriptionNumbering.reference(today.getYear(),
				numberSequences.nextReference(today.getYear()));
		String nd = SubscriptionNumbering.nd(numberSequences.nextNd());
		Subscription saved = subscriptionRepository.save(
				Subscription.create(reference, nd, client.getId(), offer, command.startDate(), today));
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("reference", saved.getReference());
		details.put("nd", saved.getNd());
		details.put("offer", offer.getCode());
		details.put("monthlyPrice", saved.getMonthlyPrice());
		details.put("startDate", saved.getStartDate().toString());
		auditTrail.record(new AuditTrail.Entry(AuditAction.SUBSCRIPTION_CREATED, AuditResource.SUBSCRIPTION,
				saved.getId(), saved.getClientId(), "Souscription " + saved.getReference() + " créée", details));
		return saved;
	}

	@Override
	public Subscription activate(Long id) {
		return transition(id, SubscriptionTransition.ACTIVATE, null, Subscription::activate);
	}

	@Override
	public Subscription suspend(Long id, String reason) {
		return transition(id, SubscriptionTransition.SUSPEND, reason, subscription -> subscription.suspend(reason));
	}

	@Override
	public Subscription reactivate(Long id) {
		return transition(id, SubscriptionTransition.REACTIVATE, null, Subscription::reactivate);
	}

	@Override
	public Subscription terminate(Long id, String reason) {
		return transition(id, SubscriptionTransition.TERMINATE, reason,
				subscription -> subscription.terminate(reason, LocalDate.now(clock)));
	}

	@Override
	public void delete(Long id) {
		Subscription subscription = lockClientThenRead(id).subscription();
		subscription.ensureDeletable();
		if (deletionPolicy.softDelete()) {
			subscriptionRepository.delete(subscription);
		}
		else {
			subscriptionRepository.deletePermanently(subscription);
		}
		auditTrail.record(new AuditTrail.Entry(AuditAction.SUBSCRIPTION_DELETED, AuditResource.SUBSCRIPTION,
				subscription.getId(), subscription.getClientId(),
				"Souscription " + subscription.getReference() + " supprimée",
				Map.of("permanent", !deletionPolicy.softDelete())));
	}

	@Override
	@Transactional(readOnly = true)
	public SubscriptionDetails get(Long id) {
		SubscriptionView subscription = subscriptionQueries.findById(id).orElseThrow(() -> subscriptionNotFound(id));
		return new SubscriptionDetails(subscription, subscriptionQueries.findHistory(id));
	}

	@Override
	@Transactional(readOnly = true)
	public SubscriptionDetails getByNd(String nd) {
		SubscriptionView subscription = subscriptionQueries.findByNd(nd)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
						"ND", nd));
		return new SubscriptionDetails(subscription, subscriptionQueries.findHistory(subscription.id()));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SubscriptionView> search(SubscriptionSearchCriteria criteria, Pageable pageable) {
		checkDateRange(criteria);
		return subscriptionQueries.search(criteria, pageable);
	}

	/** Aucune transaction englobante : chaque lot est lu dans sa propre transaction, courte. */
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public BatchedExport<SubscriptionView> export(SubscriptionSearchCriteria criteria) {
		checkDateRange(criteria);
		BatchedExport<SubscriptionView> batches = KeysetExport.of(
				(afterId, size) -> subscriptionQueries.findBatch(criteria, afterId, size), SubscriptionView::id);
		return action -> {
			long rows = batches.forEachBatch(action);
			auditTrail.record(new AuditTrail.Entry(AuditAction.SUBSCRIPTIONS_EXPORTED, AuditResource.SUBSCRIPTION, null,
					null, "Export de " + rows + " souscription(s)", Map.of("rows", rows, "filters",
							KeysetExport.usedFilters("status", criteria.status(), "technology", criteria.technology(),
									"city", criteria.city(), "nd", criteria.nd(), "startDateFrom",
									criteria.startDateFrom(), "startDateTo", criteria.startDateTo()))));
			return rows;
		};
	}

	private static void checkDateRange(SubscriptionSearchCriteria criteria) {
		if (criteria.startDateFrom() != null && criteria.startDateTo() != null
				&& criteria.startDateFrom().isAfter(criteria.startDateTo())) {
			throw new DomainValidationException(DomainErrorCode.INVALID_DATE_RANGE,
					"startDateFrom (%s) doit être antérieure ou égale à startDateTo (%s)"
							.formatted(criteria.startDateFrom(), criteria.startDateTo()));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SubscriptionView> listByClient(Long clientId, Pageable pageable) {
		if (clientRepository.findById(clientId).isEmpty()) {
			throw new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", clientId);
		}
		return subscriptionQueries.findByClient(clientId, pageable);
	}

	/**
	 * Le domaine accepte ou refuse la transition, appliquée à l'état le plus récent de la souscription
	 * (voir {@link #lockClientThenRead}). Le client est prévenu par email : la notification est
	 * enregistrée dans la même transaction que le changement de statut, et n'existe donc que s'il est
	 * validé (ADR-0021).
	 */
	private Subscription transition(Long id, SubscriptionTransition transition, String reason,
			Consumer<Subscription> operation) {
		LockedSubscription locked = lockClientThenRead(id);
		Client client = locked.client();
		Subscription subscription = locked.subscription();
		operation.accept(subscription);
		Subscription saved = subscriptionRepository.save(subscription);
		String checkedReason = transition.isReasonRequired() ? reason.trim() : null;
		notificationOutbox.enqueue(new NotificationOutbox.Request(saved.getId(), transition, client.getEmail(),
				checkedReason));
		auditTrail.record(new AuditTrail.Entry(auditAction(transition), AuditResource.SUBSCRIPTION, saved.getId(),
				saved.getClientId(), "Souscription " + saved.getReference() + " " + pastParticiple(transition),
				checkedReason == null ? Map.of() : Map.of("reason", checkedReason)));
		return saved;
	}

	/**
	 * Toute écriture sur les souscriptions d'un client commence par verrouiller la ligne du client
	 * (ADR-0025), et la souscription n'est lue qu'ensuite. Une requête simultanée sur la même
	 * souscription attend donc que la première soit validée, puis lit son résultat : une transition
	 * devenue impossible est refusée par le domaine (409 INVALID_STATUS_TRANSITION), comme si les deux
	 * requêtes s'étaient suivies. Lue avant le verrou, la souscription serait périmée, et seul le
	 * verrou optimiste détecterait le conflit (409 CONCURRENT_MODIFICATION, moins explicite).
	 */
	private LockedSubscription lockClientThenRead(Long id) {
		Long clientId = subscriptionRepository.findClientId(id).orElseThrow(() -> subscriptionNotFound(id));
		Client client = clientRepository.findByIdForUpdate(clientId)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", clientId));
		Subscription subscription = subscriptionRepository.findById(id).orElseThrow(() -> subscriptionNotFound(id));
		return new LockedSubscription(client, subscription);
	}

	private record LockedSubscription(Client client, Subscription subscription) {
	}

	private static ResourceNotFoundException subscriptionNotFound(Long id) {
		return new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription", id);
	}

	private static AuditAction auditAction(SubscriptionTransition transition) {
		return switch (transition) {
			case ACTIVATE -> AuditAction.SUBSCRIPTION_ACTIVATED;
			case SUSPEND -> AuditAction.SUBSCRIPTION_SUSPENDED;
			case REACTIVATE -> AuditAction.SUBSCRIPTION_REACTIVATED;
			case TERMINATE -> AuditAction.SUBSCRIPTION_TERMINATED;
		};
	}

	private static String pastParticiple(SubscriptionTransition transition) {
		return switch (transition) {
			case ACTIVATE -> "activée";
			case SUSPEND -> "suspendue";
			case REACTIVATE -> "réactivée";
			case TERMINATE -> "résiliée";
		};
	}

}
