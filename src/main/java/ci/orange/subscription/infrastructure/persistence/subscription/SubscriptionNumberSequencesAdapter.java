package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.application.port.out.SubscriptionNumberSequences;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Relies on PostgreSQL for uniqueness: a sequence for the ND, and an atomic upsert on a per-year
 * counter for the reference, so that concurrent requests never get the same value.
 */
@Component
class SubscriptionNumberSequencesAdapter implements SubscriptionNumberSequences {

	private final EntityManager entityManager;

	SubscriptionNumberSequencesAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public long nextNd() {
		return ((Number) entityManager.createNativeQuery("select nextval('subscription_nd_seq')").getSingleResult())
				.longValue();
	}

	@Override
	public long nextReference(int year) {
		return ((Number) entityManager.createNativeQuery("""
				insert into subscription_reference_counter (year, last_value) values (:year, 1)
				on conflict (year) do update set last_value = subscription_reference_counter.last_value + 1
				returning last_value
				""").setParameter("year", year).getSingleResult()).longValue();
	}

}
