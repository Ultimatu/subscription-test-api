package ci.orange.subscription.application.port.out;

/**
 * Unique sequence values from which the ND and the reference of a subscription are built. A value
 * is never returned twice, even to concurrent requests.
 */
public interface SubscriptionNumberSequences {

	long nextNd();

	/** Starts at 1 for every year. */
	long nextReference(int year);

}
