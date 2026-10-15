package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.application.port.in.notification.NotificationStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Une notification de l'outbox (ADR-0021). La souscription n'est référencée que par son identifiant :
 * l'envoi n'a besoin d'aucune jointure.
 */
@Getter
@Setter
@Entity
@Table(name = "notification_outbox")
public class NotificationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long subscriptionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private SubscriptionTransition transition;

	@Column(nullable = false, updatable = false)
	private String recipient;

	@Column(updatable = false, length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private Instant nextAttemptAt;

	@Column(length = 1000)
	private String lastError;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant sentAt;

}
