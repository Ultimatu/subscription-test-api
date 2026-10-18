package ci.orange.subscription.application.port.in.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port d'entrée : la suppression des clients et la conservation de leurs données (ADR-0023). Ces
 * règles évoluent avec la loi et la configuration, pas avec la gestion des clients : elles ont leur
 * propre port, distinct de {@link ClientUseCases} (ADR-0001).
 */
public interface ClientDeletionUseCases {

	/**
	 * Un client qui n'a jamais souscrit est supprimé définitivement ; un client qui a un historique
	 * est supprimé logiquement ou définitivement, selon la politique de suppression (ADR-0023).
	 */
	void delete(Long id);

	/**
	 * Supprime définitivement un client, ses souscriptions, leur historique et leurs notifications,
	 * dans la limite du délai de conservation (ADR-0023).
	 */
	void purge(Long id);

	/** Clients supprimés logiquement, en attente de purge. */
	Page<DeletedClient> listDeleted(Pageable pageable);

	/** Purge les clients dont le délai de conservation est écoulé ; renvoie leur nombre. */
	int purgeExpired();

}
