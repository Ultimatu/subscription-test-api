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

	/**
	 * Copie l'état du domaine sur une entité nouvelle ou déjà gérée ; l'identifiant, la version et
	 * l'audit restent gérés par JPA.
	 */
	static void copy(Offer offer, OfferEntity entity) {
		entity.setCode(offer.getCode());
		entity.setLabel(offer.getLabel());
		entity.setTechnology(offer.getTechnology());
		entity.setMonthlyPrice(offer.getMonthlyPrice());
		entity.setActive(offer.isActive());
	}

}
