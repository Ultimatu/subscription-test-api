package ci.orange.subscription.application.port.in.audit;

import java.time.LocalDate;

/**
 * Filtres facultatifs, combinés par un ET. Les bornes de la période sont incluses et s'entendent à
 * l'heure d'Abidjan.
 */
public record AuditSearchCriteria(AuditResource resourceType, String resourceId, String actor, AuditAction action,
		LocalDate from, LocalDate to) {
}
