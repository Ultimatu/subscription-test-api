package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Nombre de souscriptions pour chaque statut (zéro compris) et revenu mensuel des souscriptions
 * ACTIVE, les seules facturées.
 */
public record ClientSummary(Long clientId, Map<SubscriptionStatus, Long> subscriptionsByStatus,
		BigDecimal totalMonthlyRevenue) {
}
