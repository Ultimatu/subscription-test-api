package ci.orange.subscription.infrastructure.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;

import ci.orange.subscription.application.port.in.notification.NotificationStatus;
import ci.orange.subscription.application.port.in.notification.NotificationView;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class NotificationOutboxAdapterTest {

	@Autowired
	private NotificationOutbox outbox;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private EntityManager entityManager;

	private Long clientId;

	private Long subscriptionId;

	@BeforeEach
	void createSubscription() {
		clientId = clientRepository.save(Client.create("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan"))
				.getId();
		Long offerId = entityManager.createQuery("select o.id from OfferEntity o where o.code = 'FTTH-100'", Long.class)
				.getSingleResult();
		subscriptionId = subscriptionRepository.save(Subscription.create("SUB-2099-990001", "2799990001", clientId,
				offerRepository.findById(offerId).orElseThrow(), LocalDate.now(), LocalDate.now())).getId();
	}

	@Test
	void claimsADueNotificationOnlyOnceUntilItsLeaseExpires() {
		outbox.enqueue(new NotificationOutbox.Request(subscriptionId, SubscriptionTransition.SUSPEND, "awa@mail.ci",
				"Impayé"));
		Instant now = Instant.now().plus(1, ChronoUnit.SECONDS);
		Instant leaseUntil = now.plus(5, ChronoUnit.MINUTES);

		List<NotificationOutbox.Pending> claimed = outbox.claimDue(now, leaseUntil, 10);

		assertThat(claimed).singleElement().satisfies(pending -> {
			assertThat(pending.subscriptionId()).isEqualTo(subscriptionId);
			assertThat(pending.transition()).isEqualTo(SubscriptionTransition.SUSPEND);
			assertThat(pending.recipient()).isEqualTo("awa@mail.ci");
			assertThat(pending.reason()).isEqualTo("Impayé");
			assertThat(pending.attempts()).isZero();
		});
		assertThat(outbox.claimDue(now, leaseUntil, 10)).isEmpty();
		assertThat(outbox.claimDue(leaseUntil, leaseUntil.plus(5, ChronoUnit.MINUTES), 10)).hasSize(1);
	}

	@Test
	void recordsTheOutcomeOfEachAttempt() {
		outbox.enqueue(new NotificationOutbox.Request(subscriptionId, SubscriptionTransition.ACTIVATE, "awa@mail.ci",
				null));
		outbox.enqueue(new NotificationOutbox.Request(subscriptionId, SubscriptionTransition.SUSPEND, "awa@mail.ci",
				"Impayé"));
		Instant now = Instant.now().plus(1, ChronoUnit.SECONDS);
		List<NotificationOutbox.Pending> claimed = outbox.claimDue(now, now.plus(5, ChronoUnit.MINUTES), 10);
		Instant retryAt = now.plus(1, ChronoUnit.HOURS);

		outbox.markSent(claimed.get(0).id(), now);
		outbox.markFailed(claimed.get(1).id(), "Connection refused", retryAt);
		entityManager.flush();
		entityManager.clear();

		List<NotificationView> notifications = outbox.findBySubscription(subscriptionId);
		assertThat(notifications).extracting(NotificationView::transition, NotificationView::status,
				NotificationView::attempts, NotificationView::lastError)
				.containsExactly(
						tuple(SubscriptionTransition.ACTIVATE, NotificationStatus.SENT, 1,
								null),
						tuple(SubscriptionTransition.SUSPEND, NotificationStatus.PENDING, 1,
								"Connection refused"));
		assertThat(outbox.claimDue(now.plus(10, ChronoUnit.MINUTES), now.plus(15, ChronoUnit.MINUTES), 10)).isEmpty();
		assertThat(outbox.claimDue(retryAt, retryAt.plus(5, ChronoUnit.MINUTES), 10)).hasSize(1);
	}

	@Test
	void givesUpOnANotificationWithoutRetryDate() {
		outbox.enqueue(new NotificationOutbox.Request(subscriptionId, SubscriptionTransition.TERMINATE, "awa@mail.ci",
				"Déménagement"));
		Instant now = Instant.now().plus(1, ChronoUnit.SECONDS);
		Long id = outbox.claimDue(now, now.plus(5, ChronoUnit.MINUTES), 10).get(0).id();

		outbox.markFailed(id, "Connection refused", null);
		entityManager.flush();
		entityManager.clear();

		assertThat(outbox.findBySubscription(subscriptionId)).singleElement()
				.extracting(NotificationView::status).isEqualTo(NotificationStatus.FAILED);
		assertThat(outbox.claimDue(now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), 10)).isEmpty();
	}

	/** Une fois le bail posé, aucun verrou ne protège la notification : une purge peut l'effacer pendant l'envoi. */
	@Test
	void ignoresTheOutcomeOfANotificationErasedDuringItsDispatch() {
		outbox.enqueue(new NotificationOutbox.Request(subscriptionId, SubscriptionTransition.ACTIVATE, "awa@mail.ci",
				null));
		outbox.enqueue(new NotificationOutbox.Request(subscriptionId, SubscriptionTransition.SUSPEND, "awa@mail.ci",
				"Impayé"));
		Instant now = Instant.now().plus(1, ChronoUnit.SECONDS);
		List<NotificationOutbox.Pending> claimed = outbox.claimDue(now, now.plus(5, ChronoUnit.MINUTES), 10);

		clientRepository.deletePermanently(clientId);

		assertThatCode(() -> {
			outbox.markSent(claimed.get(0).id(), now);
			outbox.markFailed(claimed.get(1).id(), "Connection refused", now.plus(1, ChronoUnit.HOURS));
			entityManager.flush();
		}).doesNotThrowAnyException();
		assertThat(outbox.findBySubscription(subscriptionId)).isEmpty();
	}

}
