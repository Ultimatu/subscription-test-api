package ci.orange.subscription.application.port.in.offer;

import java.math.BigDecimal;

/**
 * Pas d'indicateur « active » : une nouvelle offre est toujours active (règle 9).
 */
public record CreateOfferCommand(String code, String label, String technology, BigDecimal monthlyPrice) {
}
