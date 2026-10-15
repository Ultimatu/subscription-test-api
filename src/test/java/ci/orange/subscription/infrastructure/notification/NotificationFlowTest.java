package ci.orange.subscription.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.notification.NotificationStatus;
import ci.orange.subscription.application.port.in.notification.NotificationUseCases;
import ci.orange.subscription.application.port.in.notification.NotificationView;
import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.Subscription;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Parcours complet, sur le vrai contexte Spring et la vraie base : le changement de statut enregistre
 * la notification, l'envoi la remet au serveur SMTP (GreenMail, en IPv4 sur le port 3025, quel que
 * soit l'hôte configuré pour le développement), avec l'attestation en pièce jointe.
 */
@SpringBootTest(properties = { "spring.mail.host=127.0.0.1", "spring.mail.port=3025" })
class NotificationFlowTest {

	@RegisterExtension
	static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

	@Autowired
	private ClientUseCases clientUseCases;

	@Autowired
	private SubscriptionUseCases subscriptionUseCases;

	@Autowired
	private NotificationUseCases notificationUseCases;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	private Long clientId;
	private Long subscriptionId;
	private Long referenceCounterBefore;

	/**
	 * L'envoi a besoin de données réellement validées : ce test ne peut pas s'exécuter dans une
	 * transaction annulée à la fin. Il note donc l'état du compteur de références, puis efface ce qu'il
	 * a créé, pour ne pas fausser les tests de persistance qui suivent.
	 */
	@BeforeEach
	void noteTheReferenceCounter() {
		referenceCounterBefore = jdbcTemplate.query(
				"select last_value from subscription_reference_counter where year = ?",
				rs -> rs.next() ? rs.getLong(1) : null, LocalDate.now(clock).getYear());
	}

	@AfterEach
	void eraseWhatThisTestCreated() {
		if (subscriptionId != null) {
			jdbcTemplate.update("delete from notification_outbox where subscription_id = ?", subscriptionId);
			jdbcTemplate.update("delete from subscription_status_history where subscription_id = ?", subscriptionId);
			jdbcTemplate.update("delete from subscription where id = ?", subscriptionId);
		}
		if (clientId != null) {
			jdbcTemplate.update("delete from client where id = ?", clientId);
		}
		int year = LocalDate.now(clock).getYear();
		if (referenceCounterBefore == null) {
			jdbcTemplate.update("delete from subscription_reference_counter where year = ?", year);
		}
		else {
			jdbcTemplate.update("update subscription_reference_counter set last_value = ? where year = ?",
					referenceCounterBefore, year);
		}
	}

	@Test
	void emailsTheClientAtActivationAndTerminationWithTheAttestation() throws Exception {
		String email = "flux." + System.nanoTime() + "@exemple.ci";
		Client client = clientUseCases.create(new CreateClientCommand("Awa", "Koné", email, "0701020304", "Abidjan"));
		clientId = client.getId();
		Long offerId = jdbcTemplate.queryForObject("select id from offer where code = 'FTTH-100'", Long.class);
		Subscription subscription = subscriptionUseCases
				.create(new CreateSubscriptionCommand(client.getId(), offerId, LocalDate.now(clock)));
		subscriptionId = subscription.getId();
		subscriptionUseCases.activate(subscription.getId());
		subscriptionUseCases.terminate(subscription.getId(), "Déménagement");

		while (notificationUseCases.dispatchDue() > 0) {
			// Envoie aussi ce que d'autres tests ont laissé en attente dans le schéma de test
		}

		List<MimeMessage> received = Arrays.stream(SMTP.getReceivedMessages())
				.filter(message -> recipients(message).contains(email))
				.toList();
		assertThat(received).extracting(MimeMessage::getSubject).containsExactly(
				"Votre ligne " + nd(subscription) + " est active", "Résiliation de votre ligne " + nd(subscription));
		// Envoyé après la résiliation, l'email d'activation n'emporte pas d'attestation périmée
		assertThat(GreenMailUtil.getBody(received.get(0))).doesNotContain("attestation-SUB-");
		assertThat(GreenMailUtil.getBody(received.get(1)))
				.contains("attestation-" + subscription.getReference() + ".pdf");
		assertThat(notificationUseCases.listForSubscription(subscription.getId()))
				.extracting(NotificationView::status)
				.containsExactly(NotificationStatus.SENT, NotificationStatus.SENT);
	}

	private static String nd(Subscription subscription) {
		return subscription.getNd().replaceAll("(\\d\\d)(?=\\d)", "$1\u00A0");
	}

	private static List<String> recipients(MimeMessage message) {
		try {
			return Arrays.stream(message.getAllRecipients()).map(Address::toString).toList();
		}
		catch (MessagingException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
