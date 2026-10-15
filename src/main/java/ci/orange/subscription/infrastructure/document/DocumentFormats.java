package ci.orange.subscription.infrastructure.document;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Mise en forme commune aux documents envoyés aux clients : attestation PDF et emails. Les espaces
 * des nombres sont des espaces insécables (U+00A0), que les polices standard du PDF savent afficher,
 * contrairement à l'espace fine insécable qu'utilise Java pour le français.
 */
public final class DocumentFormats {

	private static final Locale FRENCH = Locale.FRENCH;
	private static final char NO_BREAK_SPACE = '\u00A0';
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", FRENCH);

	private DocumentFormats() {
	}

	/** 25000.00 donne « 25 000 FCFA » ; les décimales ne s'affichent que s'il y en a (12 000,50 FCFA). */
	public static String price(BigDecimal amount) {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(FRENCH);
		symbols.setGroupingSeparator(NO_BREAK_SPACE);
		String pattern = amount.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0";
		return new DecimalFormat(pattern, symbols).format(amount) + NO_BREAK_SPACE + "FCFA";
	}

	/** « 2700000001 » donne « 27 00 00 00 01 », la présentation habituelle des numéros en Côte d'Ivoire. */
	public static String inPairs(String digits) {
		StringBuilder grouped = new StringBuilder();
		for (int i = 0; i < digits.length(); i += 2) {
			if (i > 0) {
				grouped.append(NO_BREAK_SPACE);
			}
			grouped.append(digits, i, Math.min(i + 2, digits.length()));
		}
		return grouped.toString();
	}

	/** « 15 octobre 2026 ». */
	public static String day(LocalDate date) {
		return DAY.format(date);
	}

	public static String statusLabel(SubscriptionStatus status) {
		return switch (status) {
			case PENDING -> "En attente";
			case ACTIVE -> "Active";
			case SUSPENDED -> "Suspendue";
			case TERMINATED -> "Résiliée";
		};
	}

}
