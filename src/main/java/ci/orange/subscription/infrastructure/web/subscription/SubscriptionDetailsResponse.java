package ci.orange.subscription.infrastructure.web.subscription;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionDetails;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Les champs de la souscription au premier niveau du JSON, suivis de son historique des statuts.
 */
public record SubscriptionDetailsResponse(
		@JsonUnwrapped SubscriptionResponse subscription,
		@Schema(description = "Changements de statut, du plus ancien au plus récent") List<HistoryEntry> history) {

	public record HistoryEntry(SubscriptionStatus from, SubscriptionStatus to, String reason, Instant changedAt,
			String changedBy) {
	}

	static SubscriptionDetailsResponse from(SubscriptionDetails details) {
		return new SubscriptionDetailsResponse(SubscriptionResponse.from(details.subscription()),
				details.history().stream().map(SubscriptionDetailsResponse::toEntry).toList());
	}

	private static HistoryEntry toEntry(StatusHistoryEntry entry) {
		return new HistoryEntry(entry.from(), entry.to(), entry.reason(), entry.changedAt(), entry.changedBy());
	}

}
