package ci.orange.subscription.infrastructure.persistence.notification;

import ci.orange.subscription.application.port.in.notification.NotificationStatus;
import ci.orange.subscription.application.port.in.notification.NotificationView;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.infrastructure.persistence.entity.NotificationEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class NotificationOutboxAdapter implements NotificationOutbox {

	private final NotificationJpaRepository jpaRepository;
	private final Clock clock;

	NotificationOutboxAdapter(NotificationJpaRepository jpaRepository, Clock clock) {
		this.jpaRepository = jpaRepository;
		this.clock = clock;
	}

	/** Exige la transaction du changement de statut : sans elle, l'outbox perdrait sa garantie. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueue(Request request) {
		NotificationEntity entity = new NotificationEntity();
		entity.setSubscriptionId(request.subscriptionId());
		entity.setTransition(request.transition());
		entity.setRecipient(request.recipient());
		entity.setReason(request.reason());
		entity.setStatus(NotificationStatus.PENDING);
		entity.setCreatedAt(clock.instant());
		entity.setNextAttemptAt(entity.getCreatedAt());
		jpaRepository.save(entity);
	}

	/** Le bail est posé dans la transaction du verrou : à sa validation, la réservation est acquise. */
	@Override
	@Transactional
	public List<Pending> claimDue(Instant now, Instant leaseUntil, int limit) {
		List<NotificationEntity> due = jpaRepository.lockDue(now, limit);
		due.forEach(entity -> entity.setNextAttemptAt(leaseUntil));
		return due.stream()
				.map(entity -> new Pending(entity.getId(), entity.getSubscriptionId(), entity.getTransition(),
						entity.getRecipient(), entity.getReason(), entity.getAttempts()))
				.toList();
	}

	@Override
	@Transactional
	public void markSent(Long id, Instant sentAt) {
		NotificationEntity entity = jpaRepository.findById(id).orElseThrow();
		entity.setStatus(NotificationStatus.SENT);
		entity.setAttempts(entity.getAttempts() + 1);
		entity.setSentAt(sentAt);
		entity.setLastError(null);
	}

	@Override
	@Transactional
	public void markFailed(Long id, String error, Instant retryAt) {
		NotificationEntity entity = jpaRepository.findById(id).orElseThrow();
		entity.setAttempts(entity.getAttempts() + 1);
		entity.setLastError(error);
		if (retryAt == null) {
			entity.setStatus(NotificationStatus.FAILED);
		}
		else {
			entity.setNextAttemptAt(retryAt);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<NotificationView> findBySubscription(Long subscriptionId) {
		return jpaRepository.findBySubscriptionIdOrderByIdAsc(subscriptionId).stream()
				.map(entity -> new NotificationView(entity.getId(), entity.getTransition(), entity.getRecipient(),
						entity.getStatus(), entity.getAttempts(), entity.getCreatedAt(), entity.getSentAt(),
						entity.getLastError()))
				.toList();
	}

}
