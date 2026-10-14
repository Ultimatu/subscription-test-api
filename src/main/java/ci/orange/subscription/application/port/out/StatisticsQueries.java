package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregations computed by the database. Deleted subscriptions are never counted. A null bound
 * means no limit; lower bounds are inclusive, upper {@link Instant} bounds exclusive and upper
 * {@link LocalDate} bounds inclusive. Statuses or technologies without any subscription are absent
 * from the maps.
 */
public interface StatisticsQueries {

	/** Cities are grouped regardless of case; highest revenue first. */
	List<CityStatistics> activeSubscriptionsByCity();

	Map<String, Long> activeSubscriptionsByTechnology();

	Map<SubscriptionStatus, Long> countByCurrentStatusCreatedBetween(Instant from, Instant to);

	Map<SubscriptionStatus, Long> countByCurrentStatusStartingBetween(LocalDate from, LocalDate to);

	/** Distinct subscriptions per status they entered in the period. */
	Map<SubscriptionStatus, Long> countByStatusEnteredBetween(Instant from, Instant to);

	/** Distinct subscriptions with at least one status change in the period. */
	long countChangedBetween(Instant from, Instant to);

}
