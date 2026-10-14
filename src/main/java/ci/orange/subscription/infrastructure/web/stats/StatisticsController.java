package ci.orange.subscription.infrastructure.web.stats;

import ci.orange.subscription.application.port.in.stats.PeriodBasis;
import ci.orange.subscription.application.port.in.stats.StatisticsUseCases;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import ci.orange.subscription.infrastructure.web.stats.StatisticsResponses.CityStatisticsResponse;
import ci.orange.subscription.infrastructure.web.stats.StatisticsResponses.StatusStatisticsResponse;
import ci.orange.subscription.infrastructure.web.stats.StatisticsResponses.TechnologyStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Statistiques", description = "Indicateurs sur les souscriptions")
@ApiErrors.Unauthorized
@ApiErrors.Forbidden
@RestController
@RequestMapping("/stats")
class StatisticsController {

	private final StatisticsUseCases statisticsUseCases;

	StatisticsController(StatisticsUseCases statisticsUseCases) {
		this.statisticsUseCases = statisticsUseCases;
	}

	@Operation(summary = "Souscriptions actives et revenu mensuel par ville",
			description = "Ville du client, sans tenir compte de la casse ; revenu le plus élevé en premier. Le revenu "
					+ "est la somme des prix mensuels actuels des offres souscrites.")
	@ApiResponse(responseCode = "200", description = "Une ligne par ville ayant au moins une souscription active")
	@GetMapping("/subscriptions-by-city")
	List<CityStatisticsResponse> byCity() {
		return statisticsUseCases.activeSubscriptionsByCity().stream().map(CityStatisticsResponse::from).toList();
	}

	@Operation(summary = "Répartition des souscriptions actives par technologie",
			description = "Toutes les technologies du catalogue, y compris celles sans souscription, avec leur part "
					+ "en pourcentage.")
	@ApiResponse(responseCode = "200", description = "Une ligne par technologie du catalogue")
	@GetMapping("/subscriptions-by-technology")
	List<TechnologyStatisticsResponse> byTechnology() {
		return statisticsUseCases.activeSubscriptionsByTechnology().stream().map(TechnologyStatisticsResponse::from)
				.toList();
	}

	@Operation(summary = "Nombre de souscriptions par statut sur une période",
			description = "Bornes incluses et facultatives, en dates d'Abidjan. basedOn précise la date qui doit tomber "
					+ "dans la période : CREATION_DATE (par défaut, souscriptions créées, selon leur statut actuel), "
					+ "START_DATE (souscriptions qui démarrent, selon leur statut actuel) ou STATUS_CHANGE "
					+ "(souscriptions passées à chaque statut pendant la période, d'après l'historique).")
	@ApiResponse(responseCode = "200", description = "Décompte par statut, y compris à zéro")
	@ApiErrors.BadRequest
	@GetMapping("/subscriptions-by-status")
	StatusStatisticsResponse byStatus(
			@Parameter(description = "Début de la période, inclus (AAAA-MM-JJ)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
			@Parameter(description = "Fin de la période, incluse (AAAA-MM-JJ)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
			@RequestParam(required = false) PeriodBasis basedOn) {
		return StatusStatisticsResponse.from(statisticsUseCases.subscriptionsByStatus(from, to, basedOn));
	}

}
