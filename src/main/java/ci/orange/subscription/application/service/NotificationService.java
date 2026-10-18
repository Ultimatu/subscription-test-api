package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.notification.NotificationUseCases;
import ci.orange.subscription.application.port.in.notification.NotificationView;
import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.AttestationUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.application.port.out.NotificationSender;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envoie les notifications enregistrées dans l'outbox (ADR-0013). Aucune transaction ne reste ouverte
 * pendant un envoi : la réservation d'un lot, puis le résultat de chaque envoi, sont enregistrés
 * chacun dans sa propre transaction, courte. Une notification peut être envoyée deux fois si
 * l'application s'arrête entre l'envoi et son enregistrement (livraison « au moins une fois »).
 */
@Slf4j
@Service
public class NotificationService implements NotificationUseCases {

	static final int BATCH_SIZE = 20;

	/** Durée pendant laquelle une notification réservée n'est proposée à aucune autre instance. */
	static final Duration LEASE = Duration.ofMinutes(5);

	/** Délai avant chaque nouvelle tentative ; au-delà, la notification est abandonnée. */
	static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
			Duration.ofMinutes(30), Duration.ofHours(2));

	private final NotificationOutbox outbox;
	private final NotificationSender sender;
	private final SubscriptionQueries subscriptionQueries;
	private final AttestationUseCases attestationUseCases;
	private final Clock clock;

	public NotificationService(NotificationOutbox outbox, NotificationSender sender,
			SubscriptionQueries subscriptionQueries, AttestationUseCases attestationUseCases, Clock clock) {
		this.outbox = outbox;
		this.sender = sender;
		this.subscriptionQueries = subscriptionQueries;
		this.attestationUseCases = attestationUseCases;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public int dispatchDue() {
		Instant now = clock.instant();
		List<NotificationOutbox.Pending> due = outbox.claimDue(now, now.plus(LEASE), BATCH_SIZE);
		for (NotificationOutbox.Pending notification : due) {
			dispatch(notification);
		}
		return due.size();
	}

	@Override
	@Transactional(readOnly = true)
	public List<NotificationView> listForSubscription(Long subscriptionId) {
		if (subscriptionQueries.findById(subscriptionId).isEmpty()) {
			throw new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription",
					subscriptionId);
		}
		return outbox.findBySubscription(subscriptionId);
	}

	private void dispatch(NotificationOutbox.Pending notification) {
		try {
			sender.send(message(notification));
			outbox.markSent(notification.id(), clock.instant());
		}
		catch (RuntimeException exception) {
			int attempts = notification.attempts() + 1;
			// Une souscription supprimée entre-temps (avec son client) ne reviendra pas : inutile de réessayer
			boolean permanent = exception instanceof ResourceNotFoundException;
			Instant retryAt = permanent || attempts > RETRY_DELAYS.size() ? null
					: clock.instant().plus(RETRY_DELAYS.get(attempts - 1));
			outbox.markFailed(notification.id(), describe(exception), retryAt);
			log.warn("Notification {} not sent (attempt {}): {}{}", notification.id(), attempts, describe(exception),
					retryAt == null ? ", given up" : ", next attempt at " + retryAt);
		}
	}

	/**
	 * L'attestation est jointe à l'activation et à la résiliation, les deux moments où le client en a
	 * besoin, à condition que la souscription soit encore dans l'état annoncé : l'attestation décrit
	 * l'état actuel, et une souscription déjà passée à un autre statut fera l'objet d'un email plus récent.
	 */
	private NotificationSender.Message message(NotificationOutbox.Pending notification) {
		SubscriptionView subscription = subscriptionQueries.findById(notification.subscriptionId())
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.SUBSCRIPTION_NOT_FOUND,
						"Souscription", notification.subscriptionId()));
		SubscriptionTransition transition = notification.transition();
		// Sans cas par défaut : une nouvelle transition ne compile pas tant qu'on n'a pas décidé si elle
		// joint l'attestation
		boolean attestationExpected = switch (transition) {
			case ACTIVATE, TERMINATE -> true;
			case SUSPEND, REACTIVATE -> false;
		};
		Attestation attachment = attestationExpected && subscription.status() == transition.target()
				? attestationUseCases.generate(notification.subscriptionId())
				: null;
		return new NotificationSender.Message(notification.transition(), notification.recipient(), subscription,
				notification.reason(), attachment);
	}

	private static String describe(RuntimeException exception) {
		Throwable root = exception;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
		return message.length() > 1000 ? message.substring(0, 1000) : message;
	}

}
