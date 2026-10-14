package ci.orange.subscription.infrastructure.persistence.offer;

import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;

final class OfferEntityMapper {

	private OfferEntityMapper() {
	}

	static Offer toDomain(OfferEntity entity) {
		return Offer.restore(entity.getId(), entity.getCode(), entity.getLabel(), entity.getTechnology(),
				entity.getMonthlyPrice(), entity.isActive(), entity.toAuditInfo());
	}

	/** Copies the domain state onto a new or managed entity; id, version and audit stay under JPA control. */
	static void copy(Offer offer, OfferEntity entity) {
		entity.setCode(offer.getCode());
		entity.setLabel(offer.getLabel());
		entity.setTechnology(offer.getTechnology());
		entity.setMonthlyPrice(offer.getMonthlyPrice());
		entity.setActive(offer.isActive());
	}

}
