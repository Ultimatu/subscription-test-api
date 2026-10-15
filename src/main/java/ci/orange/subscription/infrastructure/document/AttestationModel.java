package ci.orange.subscription.infrastructure.document;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.AttestationRenderer;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Données de l'attestation, déjà mises en forme pour le gabarit : le gabarit ne fait qu'afficher.
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
				DocumentFormats.inPairs(subscription.nd()),
				DocumentFormats.statusLabel(subscription.status()),
				subscription.status().name().toLowerCase(Locale.ROOT),
				holder.getFirstName() + " " + holder.getLastName(),
				holder.getEmail(),
				DocumentFormats.inPairs(holder.getPhoneNumberClient()),
				holder.getCity(),
				subscription.offer().label(),
				subscription.offer().code(),
				content.technologyLabel(),
				DocumentFormats.price(subscription.monthlyPrice()),
				DocumentFormats.day(subscription.startDate()),
				subscription.endDate() == null ? null : DocumentFormats.day(subscription.endDate()),
				terminated ? terminationReason(content.history()) : null,
				content.history().stream()
						.map(entry -> new HistoryRow(DAY_AND_TIME.format(entry.changedAt().atZone(zone)),
								operation(entry), DocumentFormats.statusLabel(entry.to()), entry.reason(),
								entry.changedBy()))
						.toList(),
				GENERATION.format(content.generatedAt()));
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
