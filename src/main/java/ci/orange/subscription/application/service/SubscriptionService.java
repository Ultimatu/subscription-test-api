package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.in.subscription.SubscriptionDetails;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
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
	private final Clock clock;

	public SubscriptionService(SubscriptionRepository subscriptionRepository, SubscriptionQueries subscriptionQueries,
			ClientRepository clientRepository, OfferRepository offerRepository,
			TechnologyRepository technologyRepository, SubscriptionNumberSequences numberSequences,
			NotificationOutbox notificationOutbox, DeletionPolicy deletionPolicy, Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.subscriptionQueries = subscriptionQueries;
		this.clientRepository = clientRepository;
		this.offerRepository = offerRepository;
		this.technologyRepository = technologyRepository;
		this.numberSequences = numberSequences;
		this.notificationOutbox = notificationOutbox;
		this.deletionPolicy = deletionPolicy;
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
		return subscriptionRepository.save(
				Subscription.create(reference, nd, client.getId(), offer, command.startDate(), today));
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
		Subscription subscription = findSubscription(id);
		subscription.ensureDeletable();
		if (deletionPolicy.softDelete()) {
			subscriptionRepository.delete(subscription);
		}
		else {
			subscriptionRepository.deletePermanently(subscription);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public SubscriptionDetails get(Long id) {
		SubscriptionView subscription = subscriptionQueries.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
						id));
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
		return KeysetExport.of((afterId, size) -> subscriptionQueries.findBatch(criteria, afterId, size),
				SubscriptionView::id);
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
	 * Le domaine accepte ou refuse la transition. Le verrou sur le client la fait passer après une
	 * éventuelle création simultanée pour le même client (règle 13) ; deux modifications
	 * simultanées de la même souscription sont détectées grâce à sa version.
	 */
	/**
	 * Le client est prévenu par email : la notification est enregistrée dans la même transaction que le
	 * changement de statut, et n'existe donc que s'il est validé (ADR-0021).
	 */
	private Subscription transition(Long id, SubscriptionTransition transition, String reason,
			Consumer<Subscription> operation) {
		Subscription subscription = findSubscription(id);
		Client client = clientRepository.findByIdForUpdate(subscription.getClientId())
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client",
						subscription.getClientId()));
		operation.accept(subscription);
		Subscription saved = subscriptionRepository.save(subscription);
		notificationOutbox.enqueue(new NotificationOutbox.Request(saved.getId(), transition, client.getEmail(),
				transition.isReasonRequired() ? reason.trim() : null));
		return saved;
	}

	private Subscription findSubscription(Long id) {
		return subscriptionRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
						id));
	}

}
