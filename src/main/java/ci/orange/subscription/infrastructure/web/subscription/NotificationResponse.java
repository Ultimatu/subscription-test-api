package ci.orange.subscription.infrastructure.web.subscription;

import ci.orange.subscription.application.port.in.notification.NotificationStatus;
import ci.orange.subscription.application.port.in.notification.NotificationView;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NotificationResponse(
		Long id,
		@Schema(description = "Opération à l'origine de la notification", example = "ACTIVATE") SubscriptionTransition transition,
		@Schema(example = "awa.kone@exemple.ci") String recipient,
		@Schema(description = "PENDING : à envoyer ; SENT : envoyée ; FAILED : abandonnée après plusieurs échecs")
		NotificationStatus status,
		int attempts,
		Instant createdAt,
		Instant sentAt,
		@Schema(description = "Cause du dernier échec d'envoi") String lastError) {

	static NotificationResponse from(NotificationView view) {
		return new NotificationResponse(view.id(), view.transition(), view.recipient(), view.status(), view.attempts(),
				view.createdAt(), view.sentAt(), view.lastError());
	}

}
