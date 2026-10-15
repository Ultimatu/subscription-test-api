package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.ClientOverview;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.OfferOverview;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SubscriptionQueryAdapter implements SubscriptionQueries {

	private final SubscriptionJpaRepository jpaRepository;
	private final SubscriptionStatusHistoryJpaRepository historyRepository;

	SubscriptionQueryAdapter(SubscriptionJpaRepository jpaRepository,
			SubscriptionStatusHistoryJpaRepository historyRepository) {
		this.jpaRepository = jpaRepository;
		this.historyRepository = historyRepository;
	}

	@Override
	public Optional<SubscriptionView> findById(Long id) {
		return jpaRepository.findWithClientAndOfferById(id).map(SubscriptionQueryAdapter::toView);
	}

	@Override
	public Optional<SubscriptionView> findByNd(String nd) {
		return jpaRepository.findWithClientAndOfferByNd(nd).map(SubscriptionQueryAdapter::toView);
	}

	@Override
	public List<StatusHistoryEntry> findHistory(Long subscriptionId) {
		return historyRepository.findBySubscriptionIdOrderByIdAsc(subscriptionId).stream()
				.map(entry -> new StatusHistoryEntry(entry.getFromStatus(), entry.getToStatus(), entry.getReason(),
						entry.getChangedAt(), entry.getChangedBy()))
				.toList();
	}

	@Override
	public Page<SubscriptionView> search(SubscriptionSearchCriteria criteria, Pageable pageable) {
		return jpaRepository.findAll(SubscriptionSpecifications.matching(criteria), pageable)
				.map(SubscriptionQueryAdapter::toView);
	}

	@Override
	public Page<SubscriptionView> findByClient(Long clientId, Pageable pageable) {
		return jpaRepository.findAll(SubscriptionSpecifications.ofClient(clientId), pageable)
				.map(SubscriptionQueryAdapter::toView);
	}

	/**
	 * Tri sur la clé primaire et limite, sans requête de comptage. Le client et l'offre sont chargés
	 * par la même requête (graphe de chargement), comme pour la recherche paginée.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<SubscriptionView> findBatch(SubscriptionSearchCriteria criteria, Long afterId, int size) {
		return jpaRepository
				.findBy(SubscriptionSpecifications.matching(criteria).and(SubscriptionSpecifications.after(afterId)),
						query -> query.project("client", "offer").sortBy(Sort.by("id")).limit(size).all())
				.stream()
				.map(SubscriptionQueryAdapter::toView)
				.toList();
	}

	private static SubscriptionView toView(SubscriptionEntity entity) {
		ClientEntity client = entity.getClient();
		OfferEntity offer = entity.getOffer();
		return new SubscriptionView(entity.getId(), entity.getReference(), entity.getNd(), entity.getStatus(),
				entity.getTechnology(), entity.getMonthlyPrice(), entity.getStartDate(), entity.getEndDate(),
				new ClientOverview(client.getId(), client.getFirstName(), client.getLastName(), client.getCity(),
						client.getStatus()),
				new OfferOverview(offer.getId(), offer.getCode(), offer.getLabel(), offer.getMonthlyPrice()),
				entity.toAuditInfo());
	}

}
