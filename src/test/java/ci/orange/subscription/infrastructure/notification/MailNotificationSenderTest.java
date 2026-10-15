package ci.orange.subscription.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.ClientOverview;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.OfferOverview;
import ci.orange.subscription.application.port.out.NotificationSender;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Envoie de vrais emails à un serveur SMTP en mémoire (GreenMail), puis les relit.
 */
class MailNotificationSenderTest {

	@RegisterExtension
	static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

	private static final SubscriptionView ACTIVE_LINE = new SubscriptionView(10L, "SUB-2026-000001", "2700000001",
			SubscriptionStatus.ACTIVE, "FTTH", new BigDecimal("25000.00"), LocalDate.of(2026, 10, 15), null,
			new ClientOverview(1L, "Awa", "Koné", "Abidjan", ClientStatus.ACTIVE),
			new OfferOverview(2L, "FTTH-100", "Fibre Confort 100 Mbps", new BigDecimal("25000.00")), null);

	@Test
	void sendsAnHtmlAndTextEmailWithTheAttestationAttached() throws Exception {
		Attestation attestation = new Attestation("attestation-SUB-2026-000001.pdf", "%PDF-1.4".getBytes());

		sender(SMTP.getSmtp().getPort()).send(new NotificationSender.Message(SubscriptionTransition.ACTIVATE,
				"awa.kone@exemple.ci", ACTIVE_LINE, null, attestation));

		MimeMessage[] received = SMTP.getReceivedMessages();
		assertThat(received).hasSize(1);
		MimeMessage email = received[0];
		assertThat(email.getSubject()).isEqualTo("Votre ligne 27\u00A000\u00A000\u00A000\u00A001 est active");
		assertThat(email.getAllRecipients()).extracting(Object::toString).containsExactly("awa.kone@exemple.ci");
		assertThat(email.getFrom()[0].toString()).contains("notifications@subscription-api.local");

		List<Part> parts = leaves(email);
		assertThat(parts).anySatisfy(part -> {
			assertThat(part.isMimeType("text/plain")).isTrue();
			assertThat((String) part.getContent()).contains("Bonjour Awa,", "Numéro de ligne : 27 00 00 00 01",
					"Votre attestation de souscription est jointe");
		});
		assertThat(parts).anySatisfy(part -> {
			assertThat(part.isMimeType("text/html")).isTrue();
			assertThat((String) part.getContent()).contains("Votre ligne est active", "Fibre Confort 100 Mbps");
		});
		assertThat(parts).anySatisfy(part -> {
			assertThat(Part.ATTACHMENT).isEqualToIgnoringCase(part.getDisposition());
			assertThat(part.getFileName()).isEqualTo("attestation-SUB-2026-000001.pdf");
			assertThat(part.isMimeType("application/pdf")).isTrue();
		});
	}

	@Test
	void sendsNoAttachmentWhenThereIsNone() throws Exception {
		sender(SMTP.getSmtp().getPort()).send(new NotificationSender.Message(SubscriptionTransition.SUSPEND,
				"awa.kone@exemple.ci", ACTIVE_LINE, "Impayé", null));

		MimeMessage email = SMTP.getReceivedMessages()[0];
		assertThat(leaves(email)).noneMatch(part -> {
			try {
				return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
			}
			catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		});
		assertThat(GreenMailUtil.getBody(email)).contains("Impay");
	}

	@Test
	void failsWhenTheMailServerIsUnreachable() {
		NotificationSender unreachable = sender(1);

		assertThatThrownBy(() -> unreachable.send(new NotificationSender.Message(SubscriptionTransition.REACTIVATE,
				"awa.kone@exemple.ci", ACTIVE_LINE, null, null))).isInstanceOf(MailException.class);
	}

	private static MailNotificationSender sender(int port) {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost("localhost");
		mailSender.setPort(port);
		return new MailNotificationSender(mailSender, templateEngine(),
				new NotificationProperties(true, "Subscription API <notifications@subscription-api.local>",
						Duration.ofSeconds(10)));
	}

	/** Parties finales du message : texte, HTML et pièces jointes, quelle que soit leur imbrication. */
	private static List<Part> leaves(Part part) throws Exception {
		List<Part> leaves = new ArrayList<>();
		if (part.getContent() instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart child = multipart.getBodyPart(i);
				leaves.addAll(leaves(child));
			}
		}
		else {
			leaves.add(part);
		}
		return leaves;
	}

	private static SpringTemplateEngine templateEngine() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(resolver);
		return engine;
	}

}
