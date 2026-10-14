package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Instant;

/**
 * One line of the status history: {@code from} is null for the creation.
 */
public record StatusHistoryEntry(SubscriptionStatus from, SubscriptionStatus to, String reason, Instant changedAt,
		String changedBy) {
}
