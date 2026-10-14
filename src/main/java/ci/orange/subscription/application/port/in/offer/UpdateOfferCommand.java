package ci.orange.subscription.application.port.in.offer;

import java.math.BigDecimal;

/**
 * Le code et la technologie ne sont plus modifiables après la création (règle 10).
 */
public record UpdateOfferCommand(String label, BigDecimal monthlyPrice) {
}
