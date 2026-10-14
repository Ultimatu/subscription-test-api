package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read side of subscription storage, separate from {@link SubscriptionRepository}: it returns read
 * models built in one query rather than domain objects. Deleted subscriptions are invisible.
 */
public interface SubscriptionQueries {

	Optional<SubscriptionView> findById(Long id);

	Optional<SubscriptionView> findByNd(String nd);

	/** Oldest change first. */
	List<StatusHistoryEntry> findHistory(Long subscriptionId);

	Page<SubscriptionView> search(SubscriptionSearchCriteria criteria, Pageable pageable);

	Page<SubscriptionView> findByClient(Long clientId, Pageable pageable);

}
