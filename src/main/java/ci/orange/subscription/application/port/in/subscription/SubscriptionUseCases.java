package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.subscription.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriptionUseCases {

	Subscription create(CreateSubscriptionCommand command);

	/** PENDING to ACTIVE. */
	Subscription activate(Long id);

	/** ACTIVE to SUSPENDED, with a mandatory reason kept in the history. */
	Subscription suspend(Long id, String reason);

	/** SUSPENDED to ACTIVE. */
	Subscription reactivate(Long id);

	/** PENDING, ACTIVE or SUSPENDED to TERMINATED, with a mandatory reason; ends the subscription today. */
	Subscription terminate(Long id, String reason);

	/** Only a PENDING subscription can be deleted (rule 22). */
	void delete(Long id);

	/** With an overview of the client and the offer, and the status history. */
	SubscriptionDetails get(Long id);

	SubscriptionDetails getByNd(String nd);

	Page<SubscriptionView> search(SubscriptionSearchCriteria criteria, Pageable pageable);

	Page<SubscriptionView> listByClient(Long clientId, Pageable pageable);

}
