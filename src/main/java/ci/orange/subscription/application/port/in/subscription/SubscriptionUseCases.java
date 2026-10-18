package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.domain.subscription.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriptionUseCases {

	Subscription create(CreateSubscriptionCommand command);

	/** PENDING vers ACTIVE. */
	Subscription activate(Long id);

	/** ACTIVE vers SUSPENDED, avec un motif obligatoire conservé dans l'historique. */
	Subscription suspend(Long id, String reason);

	/** SUSPENDED vers ACTIVE. */
	Subscription reactivate(Long id);

	/**
	 * PENDING, ACTIVE ou SUSPENDED vers TERMINATED, avec un motif obligatoire ; termine la
	 * souscription aujourd'hui.
	 */
	Subscription terminate(Long id, String reason);

	/** Seule une souscription PENDING peut être supprimée (règle 22). */
	void delete(Long id);

	/** Avec un aperçu du client et de l'offre, et l'historique des statuts. */
	SubscriptionDetails get(Long id);

	SubscriptionDetails getByNd(String nd);

	Page<SubscriptionView> search(SubscriptionSearchCriteria criteria, Pageable pageable);

	/**
	 * Toutes les souscriptions correspondant aux critères, lues par lots (ADR-0013). Les critères sont
	 * vérifiés immédiatement, comme pour la recherche.
	 */
	BatchedExport<SubscriptionView> export(SubscriptionSearchCriteria criteria);

	Page<SubscriptionView> listByClient(Long clientId, Pageable pageable);

}
