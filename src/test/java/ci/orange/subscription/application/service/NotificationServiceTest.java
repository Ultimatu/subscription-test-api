package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.AttestationUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.application.port.out.NotificationSender;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");

	@Mock
	private NotificationOutbox outbox;

	@Mock
	private NotificationSender sender;

	@Mock
	private SubscriptionQueries subscriptionQueries;

	@Mock
	private AttestationUseCases attestationUseCases;

	private NotificationService notificationService;

	private final SubscriptionView view = viewIn(SubscriptionStatus.ACTIVE);

	@BeforeEach
	void createService() {
		notificationService = new NotificationService(outbox, sender, subscriptionQueries, attestationUseCases,
				Clock.fixed(NOW, ZoneId.of("Africa/Abidjan")));
	}

	@Test
	void claimsABatchSendsEachNotificationAndRecordsTheSuccess() {
		when(outbox.claimDue(NOW, NOW.plus(NotificationService.LEASE), NotificationService.BATCH_SIZE))
				.thenReturn(List.of(pending(1L, SubscriptionTransition.SUSPEND, "Impayé", 0)));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(view));

		assertThat(notificationService.dispatchDue()).isEqualTo(1);

		verify(sender).send(new NotificationSender.Message(SubscriptionTransition.SUSPEND, "awa@mail.ci", view,
				"Impayé", null));
		verify(outbox).markSent(1L, NOW);
		verifyNoInteractions(attestationUseCases);
	}

	/** La ligne est toujours dans l'état annoncé : seule la transition décide de joindre l'attestation. */
	@ParameterizedTest
	@CsvSource({ "ACTIVATE, true", "SUSPEND, false", "REACTIVATE, false", "TERMINATE, true" })
	void attachesTheAttestationOnlyToTheActivationAndTheTermination(SubscriptionTransition transition,
			boolean attached) {
		Attestation attestation = new Attestation("attestation-SUB-2026-000001.pdf", new byte[] { 1 });
		when(outbox.claimDue(any(), any(), anyInt())).thenReturn(List.of(pending(1L, transition, null, 0)));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(viewIn(transition.target())));
		lenient().when(attestationUseCases.generate(10L)).thenReturn(attestation);

		notificationService.dispatchDue();

		ArgumentCaptor<NotificationSender.Message> message = ArgumentCaptor.forClass(NotificationSender.Message.class);
		verify(sender).send(message.capture());
		assertThat(message.getValue().attachment()).isEqualTo(attached ? attestation : null);
	}

	@Test
	void attachesNoOutdatedAttestationWhenTheLineHasChangedSince() {
		SubscriptionView terminated = new SubscriptionView(10L, "SUB-2026-000001", "2700000001",
				SubscriptionStatus.TERMINATED, "FTTH", new BigDecimal("25000.00"), LocalDate.of(2026, 10, 15),
				LocalDate.of(2026, 10, 15), null, null, null);
		when(outbox.claimDue(any(), any(), anyInt()))
				.thenReturn(List.of(pending(1L, SubscriptionTransition.ACTIVATE, null, 0)));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(terminated));

		notificationService.dispatchDue();

		ArgumentCaptor<NotificationSender.Message> message = ArgumentCaptor.forClass(NotificationSender.Message.class);
		verify(sender).send(message.capture());
		assertThat(message.getValue().attachment()).isNull();
		verifyNoInteractions(attestationUseCases);
	}

	@Test
	void retriesAFailedNotificationLater() {
		when(outbox.claimDue(any(), any(), anyInt()))
				.thenReturn(List.of(pending(1L, SubscriptionTransition.SUSPEND, "Impayé", 1)));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(view));
		doThrow(new MailSendException("Mail server connection failed", new RuntimeException("Connection refused")))
				.when(sender).send(any());

		notificationService.dispatchDue();

		verify(outbox).markFailed(1L, "Connection refused", NOW.plus(Duration.ofMinutes(5)));
		verify(outbox, never()).markSent(any(), any());
	}

	@Test
	void givesUpAfterTheLastAttempt() {
		when(outbox.claimDue(any(), any(), anyInt())).thenReturn(List.of(
				pending(1L, SubscriptionTransition.SUSPEND, "Impayé", NotificationService.RETRY_DELAYS.size())));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(view));
		doThrow(new MailSendException("Connection refused")).when(sender).send(any());

		notificationService.dispatchDue();

		verify(outbox).markFailed(1L, "Connection refused", null);
	}

	@Test
	void givesUpAtOnceWhenTheSubscriptionNoLongerExists() {
		when(outbox.claimDue(any(), any(), anyInt()))
				.thenReturn(List.of(pending(1L, SubscriptionTransition.TERMINATE, "Déménagement", 0)));
		when(subscriptionQueries.findById(10L)).thenReturn(Optional.empty());

		notificationService.dispatchDue();

		verify(outbox).markFailed(1L, "Souscription introuvable (id 10)", null);
		verifyNoInteractions(sender);
	}

	@Test
	void doesNothingWhenNothingIsDue() {
		when(outbox.claimDue(any(), any(), anyInt())).thenReturn(List.of());

		assertThat(notificationService.dispatchDue()).isZero();
		verifyNoInteractions(sender);
	}

	@Test
	void listsTheNotificationsOfAnExistingSubscriptionOnly() {
		when(subscriptionQueries.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> notificationService.listForSubscription(99L))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.SUBSCRIPTION_NOT_FOUND);
		verifyNoInteractions(outbox);
	}

	private static NotificationOutbox.Pending pending(Long id, SubscriptionTransition transition, String reason,
			int attempts) {
		return new NotificationOutbox.Pending(id, 10L, transition, "awa@mail.ci", reason, attempts);
	}

	private static SubscriptionView viewIn(SubscriptionStatus status) {
		return new SubscriptionView(10L, "SUB-2026-000001", "2700000001", status, "FTTH", new BigDecimal("25000.00"),
				LocalDate.of(2026, 10, 15), null, null, null, null);
	}

}
