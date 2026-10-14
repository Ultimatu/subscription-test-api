package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.client.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Input port: everything the outside world (here the REST API) can ask about clients.
 */
public interface ClientUseCases {

	Client create(CreateClientCommand command);

	Page<Client> search(ClientSearchCriteria criteria, Pageable pageable);

	Client get(Long id);

	Client update(Long id, UpdateClientCommand command);

	void delete(Long id);

	ClientSummary summarize(Long id);

}
