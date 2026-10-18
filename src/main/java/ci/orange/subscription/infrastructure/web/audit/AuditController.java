package ci.orange.subscription.infrastructure.web.audit;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.application.port.in.audit.AuditUseCases;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import ci.orange.subscription.infrastructure.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal d'audit des actions métier (ADR-0013), réservé aux administrateurs.
 */
@Tag(name = "Journal d'audit", description = "Qui a fait quoi, et quand (hors énoncé)")
@ApiErrors.Unauthorized
@ApiErrors.Forbidden
@RestController
@RequestMapping("/audit-events")
class AuditController {

	private final AuditUseCases auditUseCases;

	AuditController(AuditUseCases auditUseCases) {
		this.auditUseCases = auditUseCases;
	}

	@Operation(summary = "Consulter le journal d'audit",
			description = "Filtres facultatifs, combinés entre eux ; du plus récent au plus ancien par défaut.")
	@ApiResponse(responseCode = "200", description = "Page d'événements")
	@ApiErrors.BadRequest
	@GetMapping
	PageResponse<AuditEventResponse> search(
			@RequestParam(required = false) AuditResource resourceType,
			@Parameter(description = "Identifiant de la ressource (code pour une technologie)")
			@RequestParam(required = false) String resourceId,
			@Parameter(description = "Auteur exact, ex. conseiller") @RequestParam(required = false) String actor,
			@RequestParam(required = false) AuditAction action,
			@Parameter(description = "Premier jour, inclus (AAAA-MM-JJ, heure d'Abidjan)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
			@Parameter(description = "Dernier jour, inclus (AAAA-MM-JJ, heure d'Abidjan)")
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
			@ParameterObject @PageableDefault(size = 20, sort = { "occurredAt", "id" },
					direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(auditUseCases.search(
				new AuditSearchCriteria(resourceType, resourceId, actor, action, from, to), pageable),
				AuditEventResponse::from);
	}

}
