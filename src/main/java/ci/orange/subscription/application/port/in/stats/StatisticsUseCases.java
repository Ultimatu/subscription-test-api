package ci.orange.subscription.application.port.in.stats;

import java.time.LocalDate;
import java.util.List;

public interface StatisticsUseCases {

	/** Revenu le plus élevé en premier. */
	List<CityStatistics> activeSubscriptionsByCity();

	/** Toutes les technologies du catalogue, zéro compris, la plus souscrite en premier. */
	List<TechnologyStatistics> activeSubscriptionsByTechnology();

	/** Les deux bornes sont facultatives et incluses, en dates d'Abidjan. */
	StatusStatistics subscriptionsByStatus(LocalDate from, LocalDate to, PeriodBasis basedOn);

}
