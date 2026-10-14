package ci.orange.subscription.infrastructure.web.subscription;

import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SubscriptionResponse(
		Long id,
		String reference,
		String nd,
		SubscriptionStatus status,
		String technology,
		LocalDate startDate,
		LocalDate endDate,
		ClientOverview client,
		OfferOverview offer,
		Instant createdAt,
		String createdBy,
		Instant updatedAt,
		String updatedBy) {

	public record ClientOverview(Long id, String firstName, String lastName, String city, ClientStatus status) {
	}

	public record OfferOverview(Long id, String code, String label, BigDecimal monthlyPrice) {
	}

	static SubscriptionResponse from(SubscriptionView view) {
		AuditInfo audit = view.audit() != null ? view.audit() : new AuditInfo(null, null, null, null);
		ClientOverview client = view.client() == null ? null
				: new ClientOverview(view.client().id(), view.client().firstName(), view.client().lastName(),
						view.client().city(), view.client().status());
		OfferOverview offer = view.offer() == null ? null
				: new OfferOverview(view.offer().id(), view.offer().code(), view.offer().label(),
						view.offer().monthlyPrice());
		return new SubscriptionResponse(view.id(), view.reference(), view.nd(), view.status(), view.technology(),
				view.startDate(), view.endDate(), client, offer, audit.createdAt(), audit.createdBy(),
				audit.updatedAt(), audit.updatedBy());
	}

}
