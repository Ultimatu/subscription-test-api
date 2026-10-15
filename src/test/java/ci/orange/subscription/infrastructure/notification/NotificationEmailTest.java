package ci.orange.subscription.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.ClientOverview;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.OfferOverview;
import ci.orange.subscription.application.port.out.NotificationSender;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NotificationEmailTest {

	private static final Attestation ATTESTATION = new Attestation("attestation-SUB-2026-000001.pdf",
			new byte[] { 1 });

	@Test
	void announcesAnActivationWithTheAttestation() {
		NotificationEmail email = email(SubscriptionTransition.ACTIVATE, SubscriptionStatus.ACTIVE, null, null,
				ATTESTATION);

		assertThat(email.title()).isEqualTo("Votre ligne est active");
		assertThat(email.greeting()).isEqualTo("Bonjour Awa,");
		assertThat(email.dateLabel()).isEqualTo("Date de début");
		assertThat(email.date()).isEqualTo("15 octobre 2026");
		assertThat(email.attachmentNote()).contains("attestation de souscription");
		assertThat(email.reason()).isNull();
	}

	@Test
	void explainsASuspensionWithItsReasonAndWhatToDoNext() {
		NotificationEmail email = email(SubscriptionTransition.SUSPEND, SubscriptionStatus.SUSPENDED, "Impayé", null);

		assertThat(email.subject())
				.isEqualTo("Votre ligne 27\u00A000\u00A000\u00A000\u00A001 est suspendue");
		assertThat(email.statusLabel()).isEqualTo("Suspendue");
		assertThat(email.reason()).isEqualTo("Impayé");
		assertThat(email.nextStep()).contains("Contactez votre conseiller");
		assertThat(email.attachmentNote()).isNull();
	}

	@Test
	void showsTheStatusItAnnouncesEvenIfTheLineHasChangedSince() {
		NotificationEmail email = email(SubscriptionTransition.SUSPEND, SubscriptionStatus.TERMINATED, "Impayé",
				LocalDate.of(2026, 10, 20));

		assertThat(email.statusLabel()).isEqualTo("Suspendue");
		assertThat(email.dateLabel()).isEqualTo("Date de début");
	}

	@Test
	void mentionsAnAttachmentOnlyWhenThereIsOne() {
		assertThat(email(SubscriptionTransition.ACTIVATE, SubscriptionStatus.TERMINATED, null, null).attachmentNote())
				.isNull();
	}

	@Test
	void confirmsAReactivation() {
		NotificationEmail email = email(SubscriptionTransition.REACTIVATE, SubscriptionStatus.ACTIVE, null, null);

		assertThat(email.title()).isEqualTo("Votre ligne est de nouveau active");
		assertThat(email.attachmentNote()).isNull();
	}

	@Test
	void confirmsATerminationWithItsEndDateAndTheTerminationCertificate() {
		NotificationEmail email = email(SubscriptionTransition.TERMINATE, SubscriptionStatus.TERMINATED,
				"Déménagement", LocalDate.of(2026, 10, 20), ATTESTATION);

		assertThat(email.title()).isEqualTo("Votre souscription est résiliée");
		assertThat(email.dateLabel()).isEqualTo("Date de fin");
		assertThat(email.date()).isEqualTo("20 octobre 2026");
		assertThat(email.attachmentNote()).contains("attestation de résiliation");
	}

	@Test
	void offersAPlainTextVersionWithOrdinarySpaces() {
		String text = email(SubscriptionTransition.SUSPEND, SubscriptionStatus.SUSPENDED, "Impayé", null).plainText();

		assertThat(text).startsWith("Bonjour Awa,").contains("Numéro de ligne : 27 00 00 00 01",
				"Prix mensuel : 25 000 FCFA", "Motif : Impayé", "Contactez votre conseiller", "ne pas y répondre");
		assertThat(text).doesNotContain("\u00A0");
	}

	private static NotificationEmail email(SubscriptionTransition transition, SubscriptionStatus status,
			String reason, LocalDate endDate) {
		return email(transition, status, reason, endDate, null);
	}

	private static NotificationEmail email(SubscriptionTransition transition, SubscriptionStatus status,
			String reason, LocalDate endDate, Attestation attachment) {
		SubscriptionView subscription = new SubscriptionView(10L, "SUB-2026-000001", "2700000001", status, "FTTH",
				new BigDecimal("25000.00"), LocalDate.of(2026, 10, 15), endDate,
				new ClientOverview(1L, "Awa", "Koné", "Abidjan", ClientStatus.ACTIVE),
				new OfferOverview(2L, "FTTH-100", "Fibre Confort 100 Mbps", new BigDecimal("25000.00")), null);
		return NotificationEmail.from(new NotificationSender.Message(transition, "awa@mail.ci", subscription, reason,
				attachment));
	}

}
