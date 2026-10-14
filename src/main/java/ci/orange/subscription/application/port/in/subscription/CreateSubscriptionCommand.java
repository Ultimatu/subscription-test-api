package ci.orange.subscription.application.port.in.subscription;

import java.time.LocalDate;

/**
 * Ni référence ni ND : les deux sont attribués par le serveur (règles 16 à 18).
 */
public record CreateSubscriptionCommand(Long clientId, Long offerId, LocalDate startDate) {
}
