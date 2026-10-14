package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.domain.client.Client;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Output port: what the application needs from client storage. Deleted clients are invisible
 * through every method.
 */
public interface ClientRepository {

	Client save(Client client);

	Optional<Client> findById(Long id);

	boolean existsByEmail(String email);

	Page<Client> search(ClientSearchCriteria criteria, Pageable pageable);

	void delete(Client client);

}
