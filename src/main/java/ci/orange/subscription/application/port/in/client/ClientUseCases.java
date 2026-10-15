package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.application.port.in.BatchedExport;
import ci.orange.subscription.domain.client.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port d'entrée : tout ce que l'extérieur (ici l'API REST) peut demander au sujet des clients.
 */
public interface ClientUseCases {

	Client create(CreateClientCommand command);

	Page<Client> search(ClientSearchCriteria criteria, Pageable pageable);

	/** Tous les clients correspondant aux critères, lus par lots (ADR-0020). */
	BatchedExport<Client> export(ClientSearchCriteria criteria);

	Client get(Long id);

	Client update(Long id, UpdateClientCommand command);

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

	ClientSummary summarize(Long id);

}
