package ci.orange.subscription.application.port.in.stats;

import java.math.BigDecimal;

/**
 * Souscriptions ACTIVE des clients d'une ville et somme de leurs prix mensuels actuels.
 */
public record CityStatistics(String city, long activeSubscriptions, BigDecimal monthlyRevenue) {
}
