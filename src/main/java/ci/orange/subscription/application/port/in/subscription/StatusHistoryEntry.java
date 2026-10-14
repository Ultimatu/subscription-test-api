package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Instant;

/**
 * Une ligne de l'historique des statuts : {@code from} est nul pour la création.
 */
public record StatusHistoryEntry(SubscriptionStatus from, SubscriptionStatus to, String reason, Instant changedAt,
		String changedBy) {
}
