package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Côté lecture du stockage des souscriptions, séparé de {@link SubscriptionRepository} : il renvoie
 * des modèles de lecture construits en une requête plutôt que des objets du domaine. Les
 * souscriptions supprimées sont invisibles.
 */
public interface SubscriptionQueries {

	Optional<SubscriptionView> findById(Long id);

	Optional<SubscriptionView> findByNd(String nd);

	/** Du changement le plus ancien au plus récent. */
	List<StatusHistoryEntry> findHistory(Long subscriptionId);

	Page<SubscriptionView> search(SubscriptionSearchCriteria criteria, Pageable pageable);

	Page<SubscriptionView> findByClient(Long clientId, Pageable pageable);

}
