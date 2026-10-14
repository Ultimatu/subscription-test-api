package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Number of subscriptions for every status (zero included) and the monthly revenue of the ACTIVE
 * ones, the only subscriptions that are billed.
 */
public record ClientSummary(Long clientId, Map<SubscriptionStatus, Long> subscriptionsByStatus,
		BigDecimal totalMonthlyRevenue) {
}
