package ci.orange.subscription.infrastructure.notification;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.out.NotificationSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Envoie les notifications par email (SMTP) : une version HTML (gabarit Thymeleaf
 * templates/notifications/subscription-status.html), une version texte et, le cas échéant,
 * l'attestation PDF en pièce jointe.
 */
@Component
@EnableConfigurationProperties(NotificationProperties.class)
class MailNotificationSender implements NotificationSender {

	private static final String TEMPLATE = "notifications/subscription-status";

	private final JavaMailSender mailSender;
	private final ITemplateEngine templateEngine;
	private final NotificationProperties properties;

	MailNotificationSender(JavaMailSender mailSender, ITemplateEngine templateEngine,
			NotificationProperties properties) {
		this.mailSender = mailSender;
		this.templateEngine = templateEngine;
		this.properties = properties;
	}

	@Override
	public void send(Message message) {
		NotificationEmail email = NotificationEmail.from(message);
		Context context = new Context(Locale.FRENCH);
		context.setVariable("email", email);
		String html = templateEngine.process(TEMPLATE, context);

		MimeMessage mime = mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
			helper.setFrom(properties.from());
			helper.setTo(message.recipient());
			helper.setSubject(email.subject());
			helper.setText(email.plainText(), html);
			Attestation attachment = message.attachment();
			if (attachment != null) {
				helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
						Attestation.MEDIA_TYPE);
			}
		}
		catch (MessagingException exception) {
			throw new MailPreparationException("Email de notification impossible à préparer", exception);
		}
		mailSender.send(mime);
	}

}
