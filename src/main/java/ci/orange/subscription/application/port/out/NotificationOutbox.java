package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.notification.NotificationView;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import java.time.Instant;
import java.util.List;

/**
 * Notifications en attente d'envoi, conservées en base (modèle « outbox », ADR-0013).
 */
public interface NotificationOutbox {

	/** Dans la transaction en cours : la notification n'existe que si le changement de statut est validé. */
	void enqueue(Request request);

	/**
	 * Réserve au plus {@code limit} notifications dues à {@code now} : elles ne seront plus proposées,
	 * à cette instance ni à une autre, avant {@code leaseUntil}.
	 */
	List<Pending> claimDue(Instant now, Instant leaseUntil, int limit);

	void markSent(Long id, Instant sentAt);

	/** Nouvelle tentative à {@code retryAt}, ou abandon définitif si {@code retryAt} est nul. */
	void markFailed(Long id, String error, Instant retryAt);

	List<NotificationView> findBySubscription(Long subscriptionId);

	record Request(Long subscriptionId, SubscriptionTransition transition, String recipient, String reason) {
	}

	record Pending(Long id, Long subscriptionId, SubscriptionTransition transition, String recipient, String reason,
			int attempts) {
	}

}
