package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Agrégations calculées par la base de données. Les souscriptions supprimées ne sont jamais
 * comptées. Une borne nulle signifie « sans limite » ; les bornes inférieures sont incluses, les
 * bornes supérieures {@link Instant} exclues et les bornes supérieures {@link LocalDate} incluses.
 * Les statuts ou technologies sans aucune souscription sont absents des maps.
 */
public interface StatisticsQueries {

	/** Villes regroupées sans tenir compte de la casse ; revenu le plus élevé en premier. */
	List<CityStatistics> activeSubscriptionsByCity();

	Map<String, Long> activeSubscriptionsByTechnology();

	Map<SubscriptionStatus, Long> countByCurrentStatusCreatedBetween(Instant from, Instant to);

	Map<SubscriptionStatus, Long> countByCurrentStatusStartingBetween(LocalDate from, LocalDate to);

	/** Souscriptions distinctes par statut auquel elles sont passées pendant la période. */
	Map<SubscriptionStatus, Long> countByStatusEnteredBetween(Instant from, Instant to);

	/** Souscriptions distinctes ayant au moins un changement de statut pendant la période. */
	long countChangedBetween(Instant from, Instant to);

}
