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

	void delete(Long id);

	ClientSummary summarize(Long id);

}
