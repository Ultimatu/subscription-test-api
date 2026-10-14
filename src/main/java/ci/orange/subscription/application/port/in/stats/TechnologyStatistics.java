package ci.orange.subscription.application.port.in.stats;

import java.math.BigDecimal;

/**
 * ACTIVE subscriptions on a technology, and their share of all ACTIVE subscriptions in percent.
 */
public record TechnologyStatistics(String technology, String label, long activeSubscriptions, BigDecimal share) {
}
