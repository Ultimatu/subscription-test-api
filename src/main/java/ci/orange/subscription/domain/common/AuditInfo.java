package ci.orange.subscription.domain.common;

import java.time.Instant;

/**
 * Qui a créé et modifié en dernier un objet, et quand. Renseigné par la couche de persistance, en
 * lecture seule pour le domaine.
 */
public record AuditInfo(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
}
