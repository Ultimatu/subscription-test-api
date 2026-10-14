package ci.orange.subscription.domain.common;

import java.time.Instant;

/**
 * Who created and last modified an object, and when. Filled by the persistence layer, read-only
 * for the domain.
 */
public record AuditInfo(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
}
