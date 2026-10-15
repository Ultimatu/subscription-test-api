package ci.orange.subscription.application.port.in.client;

import java.time.Instant;

/**
 * Un client supprimé logiquement, conservé pendant le délai de conservation (ADR-0023).
 *
 * @param purgeableFrom date à partir de laquelle il peut être supprimé définitivement
 */
public record DeletedClient(Long id, String firstName, String lastName, String email, Instant deletedAt,
		String deletedBy, Instant purgeableFrom) {
}
