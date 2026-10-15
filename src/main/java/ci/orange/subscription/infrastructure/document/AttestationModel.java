package ci.orange.subscription.infrastructure.document;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.AttestationRenderer;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Données de l'attestation, déjà mises en forme pour le gabarit : le gabarit ne fait qu'afficher.
 * Les espaces des nombres sont des espaces insécables (U+00A0), que les polices standard du PDF
 * savent afficher, contrairement à l'espace fine insécable qu'utilise Java pour le français.
 */
record AttestationModel(
		String title,
		String reference,
		String nd,
		String statusLabel,
		String statusCss,
		String holderName,
		String holderEmail,
		String holderPhone,
		String holderCity,
		String offerLabel,
		String offerCode,
		String technologyLabel,
		String monthlyPrice,
		String startDate,
		String endDate,
		String terminationReason,
		List<HistoryRow> history,
		String generatedAt) {

	private static final Locale FRENCH = Locale.FRENCH;
	private static final char NO_BREAK_SPACE = '\u00A0';
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", FRENCH);
	private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", FRENCH);
	private static final DateTimeFormatter GENERATION = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", FRENCH);

	record HistoryRow(String date, String operation, String status, String reason, String author) {
	}

	static AttestationModel from(AttestationRenderer.Content content) {
		SubscriptionView subscription = content.subscription();
		Client holder = content.holder();
		ZoneId zone = content.generatedAt().getZone();
		boolean terminated = subscription.status() == SubscriptionStatus.TERMINATED;
		return new AttestationModel(
				terminated ? "Attestation de résiliation" : "Attestation de souscription",
				subscription.reference(),
				inPairs(subscription.nd()),
				statusLabel(subscription.status()),
				subscription.status().name().toLowerCase(Locale.ROOT),
				holder.getFirstName() + " " + holder.getLastName(),
				holder.getEmail(),
				inPairs(holder.getPhoneNumberClient()),
				holder.getCity(),
				subscription.offer().label(),
				subscription.offer().code(),
				content.technologyLabel(),
				price(subscription.monthlyPrice()),
				day(subscription.startDate()),
				subscription.endDate() == null ? null : day(subscription.endDate()),
				terminated ? terminationReason(content.history()) : null,
				content.history().stream()
						.map(entry -> new HistoryRow(DAY_AND_TIME.format(entry.changedAt().atZone(zone)),
								operation(entry), statusLabel(entry.to()), entry.reason(), entry.changedBy()))
						.toList(),
				GENERATION.format(content.generatedAt()));
	}

	/** 25000.00 donne « 25 000 FCFA » ; les décimales ne s'affichent que s'il y en a (12 000,50 FCFA). */
	static String price(BigDecimal amount) {
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(FRENCH);
		symbols.setGroupingSeparator(NO_BREAK_SPACE);
		String pattern = amount.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0";
		return new DecimalFormat(pattern, symbols).format(amount) + NO_BREAK_SPACE + "FCFA";
	}

	/** « 2700000001 » donne « 27 00 00 00 01 », la présentation habituelle des numéros en Côte d'Ivoire. */
	static String inPairs(String digits) {
		StringBuilder grouped = new StringBuilder();
		for (int i = 0; i < digits.length(); i += 2) {
			if (i > 0) {
				grouped.append(NO_BREAK_SPACE);
			}
			grouped.append(digits, i, Math.min(i + 2, digits.length()));
		}
		return grouped.toString();
	}

	static String statusLabel(SubscriptionStatus status) {
		return switch (status) {
			case PENDING -> "En attente";
			case ACTIVE -> "Active";
			case SUSPENDED -> "Suspendue";
			case TERMINATED -> "Résiliée";
		};
	}

	private static String day(LocalDate date) {
		return DAY.format(date);
	}

	private static String operation(StatusHistoryEntry entry) {
		if (entry.from() == null) {
			return "Souscription";
		}
		return switch (entry.to()) {
			case ACTIVE -> entry.from() == SubscriptionStatus.SUSPENDED ? "Réactivation" : "Activation";
			case SUSPENDED -> "Suspension";
			case TERMINATED -> "Résiliation";
			case PENDING -> "Souscription";
		};
	}

	private static String terminationReason(List<StatusHistoryEntry> history) {
		return history.stream()
				.filter(entry -> entry.to() == SubscriptionStatus.TERMINATED)
				.map(StatusHistoryEntry::reason)
				.reduce((first, second) -> second)
				.orElse(null);
	}

}
