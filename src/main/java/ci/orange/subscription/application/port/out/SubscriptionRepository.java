package ci.orange.subscription.application.port.out;

import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port de sortie pour le stockage des souscriptions. Les souscriptions supprimées sont invisibles
 * par toutes les méthodes.
 */
public interface SubscriptionRepository {

	/** Enregistre aussi dans l'historique les changements de statut consignés par la souscription. */
	Subscription save(Subscription subscription);

	Optional<Subscription> findById(Long id);

	/** Suppression logique. */
	void delete(Subscription subscription);

	/** Suppression définitive de la souscription, de son historique et de ses notifications. */
	void deletePermanently(Subscription subscription);

	/** Le client a-t-il au moins une souscription, même supprimée logiquement : un historique à conserver ? */
	boolean existsAnyForClient(Long clientId);

	/** Souscription PENDING ou ACTIVE du client sur cette technologie (règle 14). */
	boolean existsOngoingForClientAndTechnology(Long clientId, String technology);

	boolean existsByClientIdAndStatusIn(Long clientId, Set<SubscriptionStatus> statuses);

	/** Les statuts sans aucune souscription sont absents de la map. */
	Map<SubscriptionStatus, Long> countByStatusForClient(Long clientId);

	BigDecimal sumMonthlyPriceForClient(Long clientId, SubscriptionStatus status);

	void deleteAllByClientId(Long clientId);

}
