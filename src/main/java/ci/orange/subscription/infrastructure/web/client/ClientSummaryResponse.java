package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;

public record ClientSummaryResponse(Long clientId, Map<SubscriptionStatus, Long> subscriptionsByStatus,
		BigDecimal totalMonthlyRevenue) {

	static ClientSummaryResponse from(ClientSummary summary) {
		return new ClientSummaryResponse(summary.clientId(), summary.subscriptionsByStatus(),
				summary.totalMonthlyRevenue());
	}

}
