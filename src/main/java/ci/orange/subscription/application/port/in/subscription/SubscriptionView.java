package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model of a subscription with an overview of its client and its offer. Built in one query
 * by the persistence adapter: reads do not go through the domain model, which only knows ids.
 */
public record SubscriptionView(
		Long id,
		String reference,
		String nd,
		SubscriptionStatus status,
		String technology,
		LocalDate startDate,
		LocalDate endDate,
		ClientOverview client,
		OfferOverview offer,
		AuditInfo audit) {

	public record ClientOverview(Long id, String firstName, String lastName, String city, ClientStatus status) {
	}

	public record OfferOverview(Long id, String code, String label, BigDecimal monthlyPrice) {
	}

}
