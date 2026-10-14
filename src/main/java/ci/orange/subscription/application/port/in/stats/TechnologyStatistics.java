package ci.orange.subscription.application.port.in.stats;

import java.math.BigDecimal;

/**
 * Souscriptions ACTIVE sur une technologie, et leur part en pourcentage de l'ensemble des
 * souscriptions ACTIVE.
 */
public record TechnologyStatistics(String technology, String label, long activeSubscriptions, BigDecimal share) {
}
