package ci.orange.subscription.infrastructure.notification;

import ci.orange.subscription.application.port.in.notification.NotificationUseCases;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Déclenche l'envoi des notifications à intervalle régulier : l'équivalent d'un cron dans
 * l'application. Désactivable par {@code app.notifications.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
class NotificationDispatchScheduler {

	private final NotificationUseCases notificationUseCases;

	NotificationDispatchScheduler(NotificationUseCases notificationUseCases) {
		this.notificationUseCases = notificationUseCases;
	}

	/** Lot après lot, tant qu'il reste des notifications dues ; une notification en échec attend son délai. */
	@Scheduled(initialDelayString = "${app.notifications.dispatch-interval}",
			fixedDelayString = "${app.notifications.dispatch-interval}")
	void dispatch() {
		int processed;
		do {
			processed = notificationUseCases.dispatchDue();
		}
		while (processed > 0);
	}

}
