package ci.orange.subscription.infrastructure.web.audit;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditEvent;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record AuditEventResponse(
		Long id,
		Instant occurredAt,
		@Schema(example = "conseiller") String actor,
		AuditAction action,
		AuditResource resourceType,
		@Schema(description = "Identifiant de la ressource (code pour une technologie) ; absent pour un export",
				example = "42") String resourceId,
		@Schema(example = "Souscription SUB-2026-000001 suspendue") String summary,
		@Schema(description = "Précisions : champs modifiés (before, after), motif, filtres d'un export…")
		Map<String, Object> details,
		@Schema(description = "Identifiant de la requête, à rechercher dans les logs") String requestId) {

	static AuditEventResponse from(AuditEvent event) {
		return new AuditEventResponse(event.id(), event.occurredAt(), event.actor(), event.action(),
				event.resourceType(), event.resourceId(), event.summary(), event.details(), event.requestId());
	}

}
