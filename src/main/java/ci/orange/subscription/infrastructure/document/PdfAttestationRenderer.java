package ci.orange.subscription.infrastructure.document;

import ci.orange.subscription.application.port.out.AttestationRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.slf4j.Slf4jLogger;
import com.openhtmltopdf.util.XRLog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Gabarit HTML (Thymeleaf, templates/documents/attestation.html) converti en PDF par
 * openhtmltopdf. Le gabarit doit rester du XHTML bien formé : openhtmltopdf le lit comme du XML.
 */
@Slf4j
@Component
class PdfAttestationRenderer implements AttestationRenderer {

	private static final String TEMPLATE = "documents/attestation";

	/** Mêmes polices que le gabarit (Helvetica normale et grasse), pour tout charger d'avance. */
	private static final String WARM_UP_HTML =
			"<html><body style=\"font-family: Helvetica\"><p><b>-</b> -</p></body></html>";

	static {
		// Par défaut, openhtmltopdf écrit ses propres journaux, hors de la configuration de Spring
		XRLog.setLoggerImpl(new Slf4jLogger());
	}

	private final ITemplateEngine templateEngine;

	PdfAttestationRenderer(ITemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	@Override
	public byte[] render(Content content) {
		Context context = new Context(Locale.FRENCH);
		context.setVariable("attestation", AttestationModel.from(content));
		return toPdf(templateEngine.process(TEMPLATE, context));
	}

	/**
	 * La toute première génération sur une machine prend plusieurs secondes : PDFBox indexe alors les
	 * polices du système (index ensuite conservé dans ~/.pdfbox.cache) et charge ses classes. Elle est
	 * donc faite au démarrage, en arrière-plan, pour que le premier téléchargement soit immédiat.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void warmUp() {
		Thread warmUp = new Thread(() -> {
			try {
				toPdf(WARM_UP_HTML);
			}
			catch (RuntimeException exception) {
				log.warn("PDF engine warm-up failed", exception);
			}
		}, "pdf-warm-up");
		warmUp.setDaemon(true);
		warmUp.start();
	}

	private static byte[] toPdf(String html) {
		try (ByteArrayOutputStream pdf = new ByteArrayOutputStream()) {
			new PdfRendererBuilder()
					.withHtmlContent(html, null)
					.toStream(pdf)
					.run();
			return pdf.toByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Échec de la génération de l'attestation", exception);
		}
	}

}
