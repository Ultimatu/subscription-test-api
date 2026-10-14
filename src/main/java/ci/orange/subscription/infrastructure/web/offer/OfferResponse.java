package ci.orange.subscription.infrastructure.web.offer;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.offer.Offer;
import java.math.BigDecimal;
import java.time.Instant;

public record OfferResponse(
		Long id,
		String code,
		String label,
		String technology,
		BigDecimal monthlyPrice,
		boolean active,
		Instant createdAt,
		String createdBy,
		Instant updatedAt,
		String updatedBy) {

	static OfferResponse from(Offer offer) {
		AuditInfo audit = offer.getAudit() != null ? offer.getAudit() : new AuditInfo(null, null, null, null);
		return new OfferResponse(offer.getId(), offer.getCode(), offer.getLabel(), offer.getTechnology(),
				offer.getMonthlyPrice(), offer.isActive(), audit.createdAt(), audit.createdBy(), audit.updatedAt(),
				audit.updatedBy());
	}

}
