package ci.orange.subscription.application.port.in.subscription;

import java.time.LocalDate;

/**
 * No reference nor ND: both are assigned by the server (rules 16 to 18).
 */
public record CreateSubscriptionCommand(Long clientId, Long offerId, LocalDate startDate) {
}
