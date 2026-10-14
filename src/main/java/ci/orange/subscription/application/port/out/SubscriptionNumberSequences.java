package ci.orange.subscription.application.port.out;

/**
 * Valeurs de séquence uniques à partir desquelles sont construits le ND et la référence d'une
 * souscription. Une valeur n'est jamais renvoyée deux fois, même à des requêtes simultanées.
 */
public interface SubscriptionNumberSequences {

	long nextNd();

	/** Repart de 1 chaque année. */
	long nextReference(int year);

}
