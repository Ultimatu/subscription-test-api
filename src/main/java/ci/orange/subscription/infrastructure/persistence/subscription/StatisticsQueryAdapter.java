package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.application.port.out.StatisticsQueries;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Les agrégations sont calculées par PostgreSQL, jamais en chargeant les souscriptions en mémoire.
 * Les bornes facultatives n'ajoutent leur condition que lorsqu'elles sont présentes.
 */
@Repository
class StatisticsQueryAdapter implements StatisticsQueries {

	private final EntityManager entityManager;

	StatisticsQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * Une ligne par ville quelles que soient sa casse et ses accents (« Bouaké », « bouake »), affichée
	 * avec l'une de ses graphies.
	 */
	@Override
	public List<CityStatistics> activeSubscriptionsByCity() {
		return entityManager.createQuery("""
				select min(c.city), count(s), sum(s.monthlyPrice)
				from SubscriptionEntity s join s.client c
				where s.status = :active
				group by lower(function('unaccent', c.city))
				order by sum(s.monthlyPrice) desc, min(c.city)
				""", Object[].class)
				.setParameter("active", SubscriptionStatus.ACTIVE)
				.getResultList().stream()
				.map(row -> new CityStatistics((String) row[0], (Long) row[1], (BigDecimal) row[2]))
				.toList();
	}

	@Override
	public Map<String, Long> activeSubscriptionsByTechnology() {
		Map<String, Long> counts = new HashMap<>();
		entityManager.createQuery("""
				select s.technology, count(s) from SubscriptionEntity s
				where s.status = :active
				group by s.technology
				""", Object[].class)
				.setParameter("active", SubscriptionStatus.ACTIVE)
				.getResultList()
				.forEach(row -> counts.put((String) row[0], (Long) row[1]));
		return counts;
	}

	@Override
	public Map<SubscriptionStatus, Long> countByCurrentStatusCreatedBetween(Instant from, Instant to) {
		return countByStatus("select s.status, count(s) from SubscriptionEntity s where 1 = 1",
				from != null ? " and s.createdAt >= :from" : "", to != null ? " and s.createdAt < :to" : "",
				" group by s.status", from, to);
	}

	@Override
	public Map<SubscriptionStatus, Long> countByCurrentStatusStartingBetween(LocalDate from, LocalDate to) {
		return countByStatus("select s.status, count(s) from SubscriptionEntity s where 1 = 1",
				from != null ? " and s.startDate >= :from" : "", to != null ? " and s.startDate <= :to" : "",
				" group by s.status", from, to);
	}

	/** L'historique est joint à sa souscription pour écarter les souscriptions supprimées. */
	@Override
	public Map<SubscriptionStatus, Long> countByStatusEnteredBetween(Instant from, Instant to) {
		return countByStatus("""
				select h.toStatus, count(distinct s.id) from SubscriptionStatusHistoryEntity h
				join h.subscription s where s.deletedAt is null""",
				from != null ? " and h.changedAt >= :from" : "", to != null ? " and h.changedAt < :to" : "",
				" group by h.toStatus", from, to);
	}

	@Override
	public long countChangedBetween(Instant from, Instant to) {
		TypedQuery<Long> query = entityManager.createQuery("""
				select count(distinct s.id) from SubscriptionStatusHistoryEntity h
				join h.subscription s where s.deletedAt is null"""
				+ (from != null ? " and h.changedAt >= :from" : "") + (to != null ? " and h.changedAt < :to" : ""),
				Long.class);
		bind(query, from, to);
		return query.getSingleResult();
	}

	private Map<SubscriptionStatus, Long> countByStatus(String select, String fromCondition, String toCondition,
			String groupBy, Object from, Object to) {
		TypedQuery<Object[]> query = entityManager.createQuery(select + fromCondition + toCondition + groupBy,
				Object[].class);
		bind(query, from, to);
		Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
		query.getResultList().forEach(row -> counts.put((SubscriptionStatus) row[0], (Long) row[1]));
		return counts;
	}

	private static void bind(TypedQuery<?> query, Object from, Object to) {
		if (from != null) {
			query.setParameter("from", from);
		}
		if (to != null) {
			query.setParameter("to", to);
		}
	}

}
