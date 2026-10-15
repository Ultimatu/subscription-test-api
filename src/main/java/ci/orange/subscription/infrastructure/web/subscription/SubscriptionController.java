package ci.orange.subscription.infrastructure.web.subscription;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.application.port.in.notification.NotificationUseCases;
import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.AttestationUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import ci.orange.subscription.infrastructure.web.common.CsvExport;
import ci.orange.subscription.infrastructure.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Chaque écriture renvoie le détail de la souscription obtenue (client, offre et historique), relu
 * après la modification.
 */
@Slf4j
@Tag(name = "Souscriptions", description = "Souscriptions et cycle de vie")
@ApiErrors.Unauthorized
@ApiErrors.Forbidden
@RestController
@RequestMapping("/subscriptions")
class SubscriptionController {

	private static final List<String> CSV_COLUMNS = List.of("Référence", "ND", "Statut", "Technologie", "Code offre",
			"Offre", "Prix mensuel (FCFA)", "Date de début", "Date de fin", "Identifiant client", "Prénom", "Nom",
			"Ville", "Créée le", "Créée par");

	private final SubscriptionUseCases subscriptionUseCases;
	private final AttestationUseCases attestationUseCases;
	private final NotificationUseCases notificationUseCases;
	private final Clock clock;

	SubscriptionController(SubscriptionUseCases subscriptionUseCases, AttestationUseCases attestationUseCases,
			NotificationUseCases notificationUseCases, Clock clock) {
		this.subscriptionUseCases = subscriptionUseCases;
		this.attestationUseCases = attestationUseCases;
		this.notificationUseCases = notificationUseCases;
		this.clock = clock;
	}

	@Operation(summary = "Souscrire à une offre",
			description = "Crée une souscription PENDING ; la référence et le ND sont attribués par le serveur. "
					+ "Vérifications dans l'ordre : date de début, client et offre existants, client actif, offre "
					+ "active, aucune souscription suspendue, aucune souscription en cours sur la même technologie.")
	@ApiResponse(responseCode = "201", description = "Souscription créée")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PostMapping
	ResponseEntity<SubscriptionDetailsResponse> create(@Valid @RequestBody CreateSubscriptionRequest request) {
		Subscription subscription = subscriptionUseCases.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(subscription.getId())
				.toUri();
		return ResponseEntity.created(location).body(details(subscription.getId()));
	}

	@Operation(summary = "Rechercher des souscriptions", description = "Filtres optionnels, combinés entre eux.")
	@ApiResponse(responseCode = "200", description = "Page de souscriptions")
	@ApiErrors.BadRequest
	@GetMapping
	PageResponse<SubscriptionResponse> search(@RequestParam(required = false) SubscriptionStatus status,
			@Parameter(description = "Code de technologie, ex. FTTH") @RequestParam(required = false) String technology,
			@Parameter(description = "Ville du client, insensible à la casse") @RequestParam(required = false) String city,
			@Parameter(description = "ND exact") @RequestParam(required = false) String nd,
			@Parameter(description = "Date de début minimale, incluse (AAAA-MM-JJ)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDateFrom,
			@Parameter(description = "Date de début maximale, incluse (AAAA-MM-JJ)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDateTo,
			@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
		SubscriptionSearchCriteria criteria = new SubscriptionSearchCriteria(status, technology, city, nd,
				startDateFrom, startDateTo);
		return PageResponse.from(subscriptionUseCases.search(criteria, pageable), SubscriptionResponse::from);
	}

	@Operation(summary = "Exporter les souscriptions (CSV)",
			description = "Mêmes filtres que la recherche, sans pagination. Fichier CSV pour Excel : séparateur « ; », "
					+ "UTF-8 ; le prix est celui figé à la souscription. Réservé aux administrateurs.")
	@ApiResponse(responseCode = "200", description = "Fichier CSV",
			content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
	@ApiErrors.BadRequest
	@GetMapping("/export")
	void export(@RequestParam(required = false) SubscriptionStatus status,
			@Parameter(description = "Code de technologie, ex. FTTH") @RequestParam(required = false) String technology,
			@Parameter(description = "Ville du client, insensible à la casse") @RequestParam(required = false) String city,
			@Parameter(description = "ND exact") @RequestParam(required = false) String nd,
			@Parameter(description = "Date de début minimale, incluse (AAAA-MM-JJ)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDateFrom,
			@Parameter(description = "Date de début maximale, incluse (AAAA-MM-JJ)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDateTo,
			Principal principal, HttpServletResponse response) throws IOException {
		BatchedExport<SubscriptionView> export = subscriptionUseCases.export(
				new SubscriptionSearchCriteria(status, technology, city, nd, startDateFrom, startDateTo));
		long rows = CsvExport.write(response, "souscriptions-" + LocalDate.now(clock) + ".csv", CSV_COLUMNS, export,
				this::csvRow);
		log.info("Subscription export by {}: {} rows, filters: {}",
				principal == null ? "anonymous" : principal.getName(), rows,
				CsvExport.usedFilters("status", status, "technology", technology, "city", city, "nd", nd,
						"startDateFrom", startDateFrom, "startDateTo", startDateTo));
	}

	@Operation(summary = "Détail d'une souscription", description = "Avec le client, l'offre et l'historique.")
	@ApiResponse(responseCode = "200", description = "Souscription trouvée")
	@ApiErrors.NotFound
	@GetMapping("/{id}")
	SubscriptionDetailsResponse get(@PathVariable Long id) {
		return details(id);
	}

	@Operation(summary = "Télécharger l'attestation (PDF)",
			description = "Attestation de souscription, ou de résiliation pour une souscription résiliée, générée à la "
					+ "demande à partir de l'état actuel de la souscription.")
	@ApiResponse(responseCode = "200", description = "Attestation au format PDF",
			content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
					schema = @Schema(type = "string", format = "binary")))
	@ApiErrors.NotFound
	@GetMapping("/{id}/attestation")
	ResponseEntity<byte[]> attestation(@PathVariable Long id) {
		Attestation attestation = attestationUseCases.generate(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(Attestation.MEDIA_TYPE))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(attestation.fileName()).build().toString())
				.body(attestation.content());
	}

	@Operation(summary = "Notifications envoyées au client",
			description = "Un email par changement de statut, avec son état d'envoi : PENDING, SENT ou FAILED.")
	@ApiResponse(responseCode = "200", description = "Notifications, de la plus ancienne à la plus récente")
	@ApiErrors.NotFound
	@GetMapping("/{id}/notifications")
	List<NotificationResponse> notifications(@PathVariable Long id) {
		return notificationUseCases.listForSubscription(id).stream().map(NotificationResponse::from).toList();
	}

	@Operation(summary = "Retrouver une souscription par son ND")
	@ApiResponse(responseCode = "200", description = "Souscription trouvée")
	@ApiErrors.NotFound
	@GetMapping("/by-nd/{nd}")
	SubscriptionDetailsResponse getByNd(@PathVariable String nd) {
		return SubscriptionDetailsResponse.from(subscriptionUseCases.getByNd(nd));
	}

	@Operation(summary = "Activer une souscription", description = "PENDING → ACTIVE")
	@ApiResponse(responseCode = "200", description = "Souscription activée")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PostMapping("/activate")
	SubscriptionDetailsResponse activate(@Valid @RequestBody TransitionRequest request) {
		subscriptionUseCases.activate(request.subscriptionId());
		return details(request.subscriptionId());
	}

	@Operation(summary = "Suspendre une souscription", description = "ACTIVE → SUSPENDED, motif obligatoire")
	@ApiResponse(responseCode = "200", description = "Souscription suspendue")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PostMapping("/suspend")
	SubscriptionDetailsResponse suspend(@Valid @RequestBody ReasonedTransitionRequest request) {
		subscriptionUseCases.suspend(request.subscriptionId(), request.reason());
		return details(request.subscriptionId());
	}

	@Operation(summary = "Réactiver une souscription", description = "SUSPENDED → ACTIVE")
	@ApiResponse(responseCode = "200", description = "Souscription réactivée")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PostMapping("/reactivate")
	SubscriptionDetailsResponse reactivate(@Valid @RequestBody TransitionRequest request) {
		subscriptionUseCases.reactivate(request.subscriptionId());
		return details(request.subscriptionId());
	}

	@Operation(summary = "Résilier une souscription",
			description = "PENDING, ACTIVE ou SUSPENDED → TERMINATED, motif obligatoire. Renseigne la date de fin "
					+ "à aujourd'hui ; la résiliation est définitive.")
	@ApiResponse(responseCode = "200", description = "Souscription résiliée")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PostMapping("/terminate")
	SubscriptionDetailsResponse terminate(@Valid @RequestBody ReasonedTransitionRequest request) {
		subscriptionUseCases.terminate(request.subscriptionId(), request.reason());
		return details(request.subscriptionId());
	}

	@Operation(summary = "Supprimer une souscription PENDING",
			description = "Toute autre souscription doit être résiliée, pas supprimée (409).")
	@ApiResponse(responseCode = "204", description = "Souscription supprimée")
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		subscriptionUseCases.delete(id);
		return ResponseEntity.noContent().build();
	}

	private SubscriptionDetailsResponse details(Long id) {
		return SubscriptionDetailsResponse.from(subscriptionUseCases.get(id));
	}

	private List<String> csvRow(SubscriptionView subscription) {
		AuditInfo audit = subscription.audit();
		return Arrays.asList(subscription.reference(), subscription.nd(), subscription.status().name(),
				subscription.technology(), subscription.offer().code(), subscription.offer().label(),
				CsvExport.amount(subscription.monthlyPrice()), CsvExport.date(subscription.startDate()),
				CsvExport.date(subscription.endDate()), String.valueOf(subscription.client().id()),
				subscription.client().firstName(), subscription.client().lastName(), subscription.client().city(),
				audit == null ? "" : CsvExport.dateTime(audit.createdAt(), clock.getZone()),
				audit == null ? "" : audit.createdBy());
	}

}
