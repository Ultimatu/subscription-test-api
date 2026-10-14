package ci.orange.subscription.application.port.in.stats;

import java.math.BigDecimal;

/**
 * ACTIVE subscriptions of the clients of a city and the sum of their current monthly prices.
 */
public record CityStatistics(String city, long activeSubscriptions, BigDecimal monthlyRevenue) {
}
