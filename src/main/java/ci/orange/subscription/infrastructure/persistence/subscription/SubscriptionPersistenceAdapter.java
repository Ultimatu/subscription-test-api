package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.StatusChange;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionStatusHistoryEntity;
import ci.orange.subscription.infrastructure.persistence.subscription.SubscriptionJpaRepository.StatusCount;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component
class SubscriptionPersistenceAdapter implements SubscriptionRepository {

	private static final String ONGOING_TECHNOLOGY_UNIQUE_INDEX = "uk_subscription_client_technology_in_progress";

	private static final Set<SubscriptionStatus> ONGOING_STATUSES = EnumSet.of(SubscriptionStatus.PENDING,
			SubscriptionStatus.ACTIVE);

	private final SubscriptionJpaRepository jpaRepository;
	private final SubscriptionStatusHistoryJpaRepository historyRepository;
	private final EntityManager entityManager;
	private final AuditorAware<String> auditorAware;
	private final Clock clock;

	SubscriptionPersistenceAdapter(SubscriptionJpaRepository jpaRepository,
			SubscriptionStatusHistoryJpaRepository historyRepository, EntityManager entityManager,
			AuditorAware<String> auditorAware, Clock clock) {
		this.jpaRepository = jpaRepository;
		this.historyRepository = historyRepository;
		this.entityManager = entityManager;
		this.auditorAware = auditorAware;
		this.clock = clock;
	}

	@Override
	public Subscription save(Subscription subscription) {
		SubscriptionEntity entity = subscription.getId() == null ? newEntity(subscription)
				: findEntity(subscription.getId());
		entity.setStatus(subscription.getStatus());
		entity.setEndDate(subscription.getEndDate());
		SubscriptionEntity saved;
		try {
			saved = jpaRepository.saveAndFlush(entity);
		}
		catch (DataIntegrityViolationException exception) {
			// Dernier rempart pour la règle 14 si deux requêtes passaient malgré le verrou sur le
			// client
			if (exception.getCause() instanceof ConstraintViolationException violation
					&& ONGOING_TECHNOLOGY_UNIQUE_INDEX.equals(violation.getConstraintName())) {
				throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_SUBSCRIBED,
						"Le client a déjà une souscription en cours (PENDING ou ACTIVE) en technologie "
								+ entity.getTechnology());
			}
			throw exception;
		}
		for (StatusChange change : subscription.recordedStatusChanges()) {
			historyRepository.save(historyEntry(saved, change));
		}
		return SubscriptionEntityMapper.toDomain(saved);
	}

	@Override
	public Optional<Subscription> findById(Long id) {
		return jpaRepository.findById(id).map(SubscriptionEntityMapper::toDomain);
	}

	@Override
	public Optional<Long> findClientId(Long id) {
		return jpaRepository.findClientIdById(id);
	}

	@Override
	public void delete(Subscription subscription) {
		SubscriptionEntity entity = findEntity(subscription.getId());
		entity.markDeleted(clock.instant(), auditorAware.getCurrentAuditor().orElseThrow());
		jpaRepository.saveAndFlush(entity);
	}

	@Override
	public void deletePermanently(Subscription subscription) {
		entityManager.flush();
		for (String sql : List.of("delete from notification_outbox where subscription_id = :id",
				"delete from subscription_status_history where subscription_id = :id",
				"delete from subscription where id = :id")) {
			entityManager.createNativeQuery(sql).setParameter("id", subscription.getId()).executeUpdate();
		}
		entityManager.clear();
	}

	/** En SQL natif, pour compter aussi les souscriptions supprimées logiquement. */
	@Override
	public boolean existsAnyForClient(Long clientId) {
		return (Boolean) entityManager
				.createNativeQuery("select exists (select 1 from subscription where client_id = :clientId)")
				.setParameter("clientId", clientId)
				.getSingleResult();
	}

	@Override
	public boolean existsOngoingForClientAndTechnology(Long clientId, String technology) {
		return jpaRepository.existsByClientIdAndTechnologyAndStatusIn(clientId, technology, ONGOING_STATUSES);
	}

	@Override
	public boolean existsByClientIdAndStatusIn(Long clientId, Set<SubscriptionStatus> statuses) {
		return jpaRepository.existsByClientIdAndStatusIn(clientId, statuses);
	}

	@Override
	public Map<SubscriptionStatus, Long> countByStatusForClient(Long clientId) {
		Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
		for (StatusCount count : jpaRepository.countByStatusForClient(clientId)) {
			counts.put(count.getStatus(), count.getTotal());
		}
		return counts;
	}

	@Override
	public BigDecimal sumMonthlyPriceForClient(Long clientId, SubscriptionStatus status) {
		return jpaRepository.sumMonthlyPriceForClient(clientId, status);
	}

	@Override
	public void deleteAllByClientId(Long clientId) {
		Instant now = clock.instant();
		String auditor = auditorAware.getCurrentAuditor().orElseThrow();
		jpaRepository.findAllByClientId(clientId).forEach(entity -> entity.markDeleted(now, auditor));
		jpaRepository.flush();
	}

	/** La technologie est copiée depuis l'offre, qui ne la change jamais (règle 10). */
	private SubscriptionEntity newEntity(Subscription subscription) {
		OfferEntity offer = entityManager.getReference(OfferEntity.class, subscription.getOfferId());
		SubscriptionEntity entity = new SubscriptionEntity();
		entity.setReference(subscription.getReference());
		entity.setNd(subscription.getNd());
		entity.setClient(entityManager.getReference(ClientEntity.class, subscription.getClientId()));
		entity.setOffer(offer);
		entity.setTechnology(offer.getTechnology());
		entity.setMonthlyPrice(subscription.getMonthlyPrice());
		entity.setStartDate(subscription.getStartDate());
		return entity;
	}

	private SubscriptionEntity findEntity(Long id) {
		return jpaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
						id));
	}

	private static SubscriptionStatusHistoryEntity historyEntry(SubscriptionEntity subscription, StatusChange change) {
		SubscriptionStatusHistoryEntity entry = new SubscriptionStatusHistoryEntity();
		entry.setSubscription(subscription);
		entry.setFromStatus(change.from());
		entry.setToStatus(change.to());
		entry.setReason(change.reason());
		return entry;
	}

}
