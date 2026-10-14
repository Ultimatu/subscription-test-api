package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.in.subscription.SubscriptionDetails;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionNumberSequences;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionEligibility;
import ci.orange.subscription.domain.subscription.SubscriptionNumbering;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubscriptionService implements SubscriptionUseCases {

	private final SubscriptionRepository subscriptionRepository;
	private final SubscriptionQueries subscriptionQueries;
	private final ClientRepository clientRepository;
	private final OfferRepository offerRepository;
	private final SubscriptionNumberSequences numberSequences;
	private final Clock clock;

	public SubscriptionService(SubscriptionRepository subscriptionRepository, SubscriptionQueries subscriptionQueries,
			ClientRepository clientRepository, OfferRepository offerRepository,
			SubscriptionNumberSequences numberSequences, Clock clock) {
		this.subscriptionRepository = subscriptionRepository;
		this.subscriptionQueries = subscriptionQueries;
		this.clientRepository = clientRepository;
		this.offerRepository = offerRepository;
		this.numberSequences = numberSequences;
		this.clock = clock;
	}

	/**
	 * Invalid data first, then unknown references, then rules 12 to 14 in the imposed order. The
	 * client stays locked until the end of the transaction, so that a concurrent request for the same
	 * client cannot pass the same checks at the same time. Numbers are only drawn once every check
	 * has passed.
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

		SubscriptionEligibility.check(client, offer,
				subscriptionRepository.existsByClientIdAndStatusIn(client.getId(),
						EnumSet.of(SubscriptionStatus.SUSPENDED)),
				subscriptionRepository.existsOngoingForClientAndTechnology(client.getId(), offer.getTechnology()));

		String reference = SubscriptionNumbering.reference(today.getYear(),
				numberSequences.nextReference(today.getYear()));
		String nd = SubscriptionNumbering.nd(numberSequences.nextNd());
		return subscriptionRepository.save(
				Subscription.create(reference, nd, client.getId(), offer.getId(), command.startDate(), today));
	}

	@Override
	public Subscription activate(Long id) {
		return transition(id, Subscription::activate);
	}

	@Override
	public Subscription suspend(Long id, String reason) {
		return transition(id, subscription -> subscription.suspend(reason));
	}

	@Override
	public Subscription reactivate(Long id) {
		return transition(id, Subscription::reactivate);
	}

	@Override
	public Subscription terminate(Long id, String reason) {
		return transition(id, subscription -> subscription.terminate(reason, LocalDate.now(clock)));
	}

	@Override
	public void delete(Long id) {
		Subscription subscription = findSubscription(id);
		subscription.ensureDeletable();
		subscriptionRepository.delete(subscription);
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
		if (criteria.startDateFrom() != null && criteria.startDateTo() != null
				&& criteria.startDateFrom().isAfter(criteria.startDateTo())) {
			throw new DomainValidationException(DomainErrorCode.INVALID_DATE_RANGE,
					"startDateFrom (%s) doit être antérieure ou égale à startDateTo (%s)"
							.formatted(criteria.startDateFrom(), criteria.startDateTo()));
		}
		return subscriptionQueries.search(criteria, pageable);
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
	 * The domain accepts or refuses the transition. Locking the client serializes it with a concurrent
	 * creation for the same client (rule 13); two concurrent changes of the same subscription are
	 * caught by its version.
	 */
	private Subscription transition(Long id, Consumer<Subscription> operation) {
		Subscription subscription = findSubscription(id);
		clientRepository.findByIdForUpdate(subscription.getClientId());
		operation.accept(subscription);
		return subscriptionRepository.save(subscription);
	}

	private Subscription findSubscription(Long id) {
		return subscriptionRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
						id));
	}

}
