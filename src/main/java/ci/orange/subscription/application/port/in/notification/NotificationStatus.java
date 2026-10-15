package ci.orange.subscription.application.port.in.notification;

/**
 * PENDING : à envoyer, éventuellement après un échec ; SENT : envoyée ; FAILED : abandonnée après le
 * nombre maximal de tentatives.
 */
public enum NotificationStatus {
	PENDING, SENT, FAILED
}
