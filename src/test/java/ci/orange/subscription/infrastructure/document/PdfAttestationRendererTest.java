package ci.orange.subscription.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Génère un vrai PDF à partir du vrai gabarit, puis en relit le texte avec PDFBox.
 */
class PdfAttestationRendererTest {

	private final PdfAttestationRenderer renderer = new PdfAttestationRenderer(templateEngine());

	@Test
	void producesAOnePagePdfWithTheSubscriptionDetails() throws IOException {
		byte[] pdf = renderer.render(AttestationFixtures.content(SubscriptionStatus.ACTIVE));

		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(1);
			String text = text(document);
			assertThat(text).contains("Attestation de souscription", "SUB-2026-000001", "27 00 00 00 01", "Awa Koné",
					"awa.kone@exemple.ci", "07 01 02 03 04", "Fibre Confort 100 Mbps", "FTTH-100", "Fibre optique",
					"25 000 FCFA", "15 octobre 2026", "Impayé", "Réactivation", "sans valeur contractuelle");
			assertThat(text).doesNotContain("Attestation de résiliation", "Déménagement");
		}
	}

	@Test
	void describesTheTerminationOfATerminatedSubscription() throws IOException {
		byte[] pdf = renderer.render(AttestationFixtures.content(SubscriptionStatus.TERMINATED));

		try (PDDocument document = Loader.loadPDF(pdf)) {
			String text = text(document);
			assertThat(text).contains("Attestation de résiliation", "Résiliée", "Du 15 octobre 2026 au 20 octobre 2026",
					"Motif : Déménagement");
		}
	}

	/**
	 * Texte dans l'ordre de lecture (et non dans l'ordre d'écriture du PDF), avec des espaces
	 * ordinaires à la place des espaces insécables, plus simples à comparer.
	 */
	private static String text(PDDocument document) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setSortByPosition(true);
		return stripper.getText(document).replace('\u00A0', ' ').replaceAll("\\s+", " ");
	}

	/** Même résolution des gabarits que celle de Spring Boot : classpath:/templates/, suffixe .html. */
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
