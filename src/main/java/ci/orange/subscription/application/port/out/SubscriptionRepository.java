package ci.orange.subscription.application.port.out;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Output port for subscription storage. Deleted subscriptions are invisible through every method.
 */
public interface SubscriptionRepository {

	boolean existsByClientIdAndStatusIn(Long clientId, Set<SubscriptionStatus> statuses);

	/** Statuses without any subscription are absent from the map. */
	Map<SubscriptionStatus, Long> countByStatusForClient(Long clientId);

	BigDecimal sumMonthlyPriceForClient(Long clientId, SubscriptionStatus status);

	void deleteAllByClientId(Long clientId);

}
