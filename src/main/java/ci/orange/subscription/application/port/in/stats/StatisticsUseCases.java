package ci.orange.subscription.application.port.in.stats;

import java.time.LocalDate;
import java.util.List;

public interface StatisticsUseCases {

	/** Highest revenue first. */
	List<CityStatistics> activeSubscriptionsByCity();

	/** Every technology of the catalogue, zero included, most subscribed first. */
	List<TechnologyStatistics> activeSubscriptionsByTechnology();

	/** Both bounds are optional and inclusive, as dates in Abidjan. */
	StatusStatistics subscriptionsByStatus(LocalDate from, LocalDate to, PeriodBasis basedOn);

}
