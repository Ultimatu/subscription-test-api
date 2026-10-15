package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SubscriptionJpaRepository
		extends JpaRepository<SubscriptionEntity, Long>, JpaSpecificationExecutor<SubscriptionEntity> {

	/** Côté lecture : le client et l'offre sont chargés dans la même requête (pas de N+1). */
	@EntityGraph(attributePaths = { "client", "offer" })
	Optional<SubscriptionEntity> findWithClientAndOfferById(Long id);

	@EntityGraph(attributePaths = { "client", "offer" })
	Optional<SubscriptionEntity> findWithClientAndOfferByNd(String nd);

	@Override
	@EntityGraph(attributePaths = { "client", "offer" })
	Page<SubscriptionEntity> findAll(Specification<SubscriptionEntity> specification, Pageable pageable);

	@Query("select s.client.id from SubscriptionEntity s where s.id = :id")
	Optional<Long> findClientIdById(@Param("id") Long id);

	boolean existsByClientIdAndStatusIn(Long clientId, Collection<SubscriptionStatus> statuses);

	boolean existsByClientIdAndTechnologyAndStatusIn(Long clientId, String technology,
			Collection<SubscriptionStatus> statuses);

	List<SubscriptionEntity> findAllByClientId(Long clientId);

	@Query("""
			select s.status as status, count(s) as total
			from SubscriptionEntity s
			where s.client.id = :clientId
			group by s.status
			""")
	List<StatusCount> countByStatusForClient(@Param("clientId") Long clientId);

	@Query("""
			select coalesce(sum(s.monthlyPrice), 0)
			from SubscriptionEntity s
			where s.client.id = :clientId and s.status = :status
			""")
	BigDecimal sumMonthlyPriceForClient(@Param("clientId") Long clientId, @Param("status") SubscriptionStatus status);

	interface StatusCount {

		SubscriptionStatus getStatus();

		long getTotal();

	}

}
