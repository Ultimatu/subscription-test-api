package ci.orange.subscription.application.port.in.stats;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.LocalDate;
import java.util.Map;

/**
 * Number of subscriptions for every status (zero included) over a period. {@code total} is the
 * number of distinct subscriptions counted: with {@link PeriodBasis#STATUS_CHANGE}, a subscription
 * can appear under several statuses but counts once in the total.
 */
public record StatusStatistics(LocalDate from, LocalDate to, PeriodBasis basedOn,
		Map<SubscriptionStatus, Long> subscriptionsByStatus, long total) {
}
