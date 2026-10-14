package ci.orange.subscription.application.port.in.offer;

import java.math.BigDecimal;

/**
 * Code and technology cannot change after creation (rule 10).
 */
public record UpdateOfferCommand(String label, BigDecimal monthlyPrice) {
}
