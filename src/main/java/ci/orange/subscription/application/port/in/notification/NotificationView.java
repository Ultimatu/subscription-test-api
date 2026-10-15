package ci.orange.subscription.application.port.in.notification;

import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import java.time.Instant;

/**
 * Une notification envoyée, ou à envoyer, au client d'une souscription.
 */
public record NotificationView(Long id, SubscriptionTransition transition, String recipient, NotificationStatus status,
		int attempts, Instant createdAt, Instant sentAt, String lastError) {
}
