package ci.orange.subscription.infrastructure.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés {@code app.notifications.*} (ADR-0013).
 *
 * @param enabled envoi planifié des emails ; les notifications sont enregistrées dans tous les cas
 * @param from expéditeur des emails
 * @param dispatchInterval délai entre deux passages de l'envoi
 */
@ConfigurationProperties("app.notifications")
record NotificationProperties(boolean enabled, String from, Duration dispatchInterval) {
}
