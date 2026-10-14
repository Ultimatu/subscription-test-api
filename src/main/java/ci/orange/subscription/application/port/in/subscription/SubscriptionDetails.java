package ci.orange.subscription.application.port.in.subscription;

import java.util.List;

/**
 * A subscription with its whole status history, oldest change first.
 */
public record SubscriptionDetails(SubscriptionView subscription, List<StatusHistoryEntry> history) {
}
