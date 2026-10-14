package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;

final class SubscriptionEntityMapper {

	private SubscriptionEntityMapper() {
	}

	/** Reading the ids of the lazy client and offer does not load them. */
	static Subscription toDomain(SubscriptionEntity entity) {
		return Subscription.restore(entity.getId(), entity.getReference(), entity.getNd(), entity.getClient().getId(),
				entity.getOffer().getId(), entity.getStatus(), entity.getStartDate(), entity.getEndDate(),
				entity.toAuditInfo());
	}

}
