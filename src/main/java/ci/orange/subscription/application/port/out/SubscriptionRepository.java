package ci.orange.subscription.application.port.out;

import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Output port for subscription storage. Deleted subscriptions are invisible through every method.
 */
public interface SubscriptionRepository {

	/** Also stores the status changes recorded by the subscription in its history. */
	Subscription save(Subscription subscription);

	Optional<Subscription> findById(Long id);

	void delete(Subscription subscription);

	/** PENDING or ACTIVE subscription of the client on that technology (rule 14). */
	boolean existsOngoingForClientAndTechnology(Long clientId, String technology);

	boolean existsByClientIdAndStatusIn(Long clientId, Set<SubscriptionStatus> statuses);

	/** Statuses without any subscription are absent from the map. */
	Map<SubscriptionStatus, Long> countByStatusForClient(Long clientId);

	BigDecimal sumMonthlyPriceForClient(Long clientId, SubscriptionStatus status);

	void deleteAllByClientId(Long clientId);

}
