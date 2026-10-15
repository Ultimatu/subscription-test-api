package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;

final class SubscriptionEntityMapper {

	private SubscriptionEntityMapper() {
	}

	/** Lire l'identifiant du client et de l'offre, chargés à la demande, ne les charge pas. */
	static Subscription toDomain(SubscriptionEntity entity) {
		return Subscription.restore(entity.getId(), entity.getReference(), entity.getNd(), entity.getClient().getId(),
				entity.getOffer().getId(), entity.getMonthlyPrice(), entity.getStatus(), entity.getStartDate(),
				entity.getEndDate(), entity.toAuditInfo());
	}

}
