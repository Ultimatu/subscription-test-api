package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.subscription.Subscription;

public interface SubscriptionUseCases {

	Subscription create(CreateSubscriptionCommand command);

}
