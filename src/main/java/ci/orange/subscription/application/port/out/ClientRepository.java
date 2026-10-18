package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.domain.client.Client;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port de sortie : ce dont l'application a besoin pour stocker les clients. Les clients supprimés
 * sont invisibles par toutes les méthodes.
 */
public interface ClientRepository {

	Client save(Client client);

	Optional<Client> findById(Long id);

	/**
	 * Comme {@link #findById(Long)}, mais verrouille le client jusqu'à la fin de la transaction :
	 * les opérations sur les souscriptions d'un même client s'exécutent alors l'une après l'autre
	 * (ADR-0014).
	 */
	Optional<Client> findByIdForUpdate(Long id);

	boolean existsByEmail(String email);

	Page<Client> search(ClientSearchCriteria criteria, Pageable pageable);

	/**
	 * Au plus {@code size} clients correspondant aux critères, d'identifiant strictement supérieur à
	 * {@code afterId} (tous si nul), triés par identifiant : un lot d'export.
	 */
	List<Client> findBatch(ClientSearchCriteria criteria, Long afterId, int size);

	/** Suppression logique : le client disparaît de l'API mais reste en base. */
	void delete(Client client);

	/** Date de la suppression logique, si le client a été supprimé logiquement. */
	Optional<Instant> findDeletionDate(Long id);

	/** Clients supprimés logiquement, du plus récent au plus ancien. */
	Page<Deleted> findDeleted(Pageable pageable);

	/** Identifiants des clients supprimés logiquement avant cette date. */
	List<Long> findDeletedBefore(Instant instant);

	/** Suppression définitive du client, de ses souscriptions, de leur historique et de leurs notifications. */
	void deletePermanently(Long id);

	record Deleted(Long id, String firstName, String lastName, String email, Instant deletedAt, String deletedBy) {
	}

}
