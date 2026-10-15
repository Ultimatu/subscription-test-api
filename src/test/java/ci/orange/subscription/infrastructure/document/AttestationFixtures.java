package ci.orange.subscription.infrastructure.document;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.ClientOverview;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.OfferOverview;
import ci.orange.subscription.application.port.out.AttestationRenderer;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Souscription de démonstration : activée, suspendue pour impayé, réactivée puis, selon le cas,
 * résiliée.
 */
final class AttestationFixtures {

	static final ZonedDateTime GENERATED_AT = ZonedDateTime.of(2026, 10, 20, 9, 30, 0, 0,
			ZoneId.of("Africa/Abidjan"));

	private AttestationFixtures() {
	}

	static AttestationRenderer.Content content(SubscriptionStatus status) {
		boolean terminated = status == SubscriptionStatus.TERMINATED;
		SubscriptionView subscription = new SubscriptionView(10L, "SUB-2026-000001", "2700000001", status, "FTTH",
				new BigDecimal("25000.00"), LocalDate.of(2026, 10, 15), terminated ? LocalDate.of(2026, 10, 20) : null,
				new ClientOverview(1L, "Awa", "Koné", "Abidjan", ClientStatus.ACTIVE),
				new OfferOverview(2L, "FTTH-100", "Fibre Confort 100 Mbps", new BigDecimal("27000.00")), null);
		Client holder = Client.restore(1L, "Awa", "Koné", "awa.kone@exemple.ci", "0701020304", "Abidjan",
				ClientStatus.ACTIVE, null);
		List<StatusHistoryEntry> history = new ArrayList<>(List.of(
				entry(null, SubscriptionStatus.PENDING, null, "2026-10-15T08:00:00Z"),
				entry(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, null, "2026-10-15T08:05:00Z"),
				entry(SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED, "Impayé", "2026-10-17T10:00:00Z"),
				entry(SubscriptionStatus.SUSPENDED, SubscriptionStatus.ACTIVE, null, "2026-10-18T14:30:00Z")));
		if (terminated) {
			history.add(entry(SubscriptionStatus.ACTIVE, SubscriptionStatus.TERMINATED, "Déménagement",
					"2026-10-20T09:00:00Z"));
		}
		return new AttestationRenderer.Content(subscription, history, holder, "Fibre optique", GENERATED_AT);
	}

	private static StatusHistoryEntry entry(SubscriptionStatus from, SubscriptionStatus to, String reason,
			String changedAt) {
		return new StatusHistoryEntry(from, to, reason, Instant.parse(changedAt), "conseiller");
	}

}
