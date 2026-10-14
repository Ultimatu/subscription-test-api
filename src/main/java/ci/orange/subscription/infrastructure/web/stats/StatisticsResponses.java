package ci.orange.subscription.infrastructure.web.stats;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.application.port.in.stats.PeriodBasis;
import ci.orange.subscription.application.port.in.stats.StatusStatistics;
import ci.orange.subscription.application.port.in.stats.TechnologyStatistics;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Response bodies of the statistics endpoints.
 */
final class StatisticsResponses {

	private StatisticsResponses() {
	}

	record CityStatisticsResponse(
			@Schema(example = "Abidjan") String city,
			@Schema(example = "2") long activeSubscriptions,
			@Schema(description = "Somme des prix mensuels actuels des souscriptions ACTIVE", example = "60000.00")
			BigDecimal monthlyRevenue) {

		static CityStatisticsResponse from(CityStatistics statistics) {
			return new CityStatisticsResponse(statistics.city(), statistics.activeSubscriptions(),
					statistics.monthlyRevenue());
		}

	}

	record TechnologyStatisticsResponse(
			@Schema(example = "FTTH") String technology,
			@Schema(example = "Fibre optique") String label,
			@Schema(example = "2") long activeSubscriptions,
			@Schema(description = "Part des souscriptions ACTIVE, en pourcentage", example = "66.67") BigDecimal share) {

		static TechnologyStatisticsResponse from(TechnologyStatistics statistics) {
			return new TechnologyStatisticsResponse(statistics.technology(), statistics.label(),
					statistics.activeSubscriptions(), statistics.share());
		}

	}

	record StatusStatisticsResponse(
			LocalDate from,
			LocalDate to,
			PeriodBasis basedOn,
			Map<SubscriptionStatus, Long> subscriptionsByStatus,
			@Schema(description = "Nombre de souscriptions distinctes comptées") long total) {

		static StatusStatisticsResponse from(StatusStatistics statistics) {
			return new StatusStatisticsResponse(statistics.from(), statistics.to(), statistics.basedOn(),
					statistics.subscriptionsByStatus(), statistics.total());
		}

	}

}
