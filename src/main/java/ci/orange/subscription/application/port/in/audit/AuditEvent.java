package ci.orange.subscription.application.port.in.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Une action consignée dans le journal d'audit.
 *
 * @param resourceId identifiant de la ressource (code pour une technologie), nul pour un export
 * @param details précisions propres à l'action : champs modifiés (avant, après), motif, filtres…
 * @param requestId identifiant de la requête HTTP, pour retrouver ses lignes de log ; nul pour une tâche planifiée
 */
public record AuditEvent(Long id, Instant occurredAt, String actor, AuditAction action, AuditResource resourceType,
		String resourceId, String summary, Map<String, Object> details, String requestId) {
}
