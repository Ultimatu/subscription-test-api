package ci.orange.subscription.application.port.in.offer;

import java.math.BigDecimal;

/**
 * No "active" flag: a new offer is always active (rule 9).
 */
public record CreateOfferCommand(String code, String label, String technology, BigDecimal monthlyPrice) {
}
