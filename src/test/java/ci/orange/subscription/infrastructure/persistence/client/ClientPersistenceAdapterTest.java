package ci.orange.subscription.infrastructure.persistence.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@PersistenceAdapterTest
class ClientPersistenceAdapterTest {

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesAndReloadsAClientWithItsAuditTrail() {
		Client saved = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		Client reloaded = clientRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getEmail()).isEqualTo("awa@mail.ci");
		assertThat(reloaded.getStatus()).isEqualTo(ClientStatus.ACTIVE);
		assertThat(reloaded.getAudit().createdAt()).isNotNull();
		assertThat(reloaded.getAudit().createdBy()).isEqualTo("anonymous");
	}

	@Test
	void findsAClientForUpdateUnlessItIsDeleted() {
		Client client = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		assertThat(clientRepository.findByIdForUpdate(client.getId())).map(Client::getEmail).contains("awa@mail.ci");

		clientRepository.delete(client);
		entityManager.clear();

		assertThat(clientRepository.findByIdForUpdate(client.getId())).isEmpty();
	}

	@Test
	void updatesAnExistingClient() {
		Client client = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));
		client.update("Aya", "Koné", "0501020304", "Bouaké", ClientStatus.INACTIVE);

		Client updated = clientRepository.save(client);

		assertThat(updated.getId()).isEqualTo(client.getId());
		assertThat(updated.getCity()).isEqualTo("Bouaké");
		assertThat(updated.getStatus()).isEqualTo(ClientStatus.INACTIVE);
	}

	@Test
	void detectsAnExistingEmailWhateverTheCase() {
		clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		assertThat(clientRepository.existsByEmail("AWA@mail.ci")).isTrue();
		assertThat(clientRepository.existsByEmail("other@mail.ci")).isFalse();
	}

	@Test
	void theDatabaseRejectsADuplicateEmailThatSlippedThroughTheCheck() {
		clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		assertThatThrownBy(() -> clientRepository.save(newClient("Other", "Person", "awa@mail.ci", "Abidjan")))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_EMAIL_ALREADY_USED);
	}

	@Test
	void searchesByCityStatusAndPartialCaseInsensitiveText() {
		clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));
		clientRepository.save(newClient("Moussa", "Konaté", "moussa@mail.ci", "abidjan"));
		clientRepository.save(newClient("Aya", "Traoré", "aya.kone@mail.ci", "Bouaké"));
		Client inactive = clientRepository.save(newClient("Koffi", "Yao", "koffi@mail.ci", "Abidjan"));
		inactive.update("Koffi", "Yao", "0701020304", "Abidjan", ClientStatus.INACTIVE);
		clientRepository.save(inactive);

		assertThat(searchEmails(new ClientSearchCriteria(null, null, "KON")))
				.containsExactly("awa@mail.ci", "aya.kone@mail.ci", "moussa@mail.ci");
		assertThat(searchEmails(new ClientSearchCriteria("ABIDJAN", ClientStatus.ACTIVE, null)))
				.containsExactly("awa@mail.ci", "moussa@mail.ci");
		assertThat(searchEmails(new ClientSearchCriteria(" abidjan ", null, "kon")))
				.containsExactly("awa@mail.ci", "moussa@mail.ci");
	}

	@Test
	void readsAnExportBatchByBatchInTheOrderOfIdentifiers() {
		Long awa = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan")).getId();
		clientRepository.save(newClient("Aya", "Traoré", "aya@mail.ci", "Bouaké"));
		Long moussa = clientRepository.save(newClient("Moussa", "Konaté", "moussa@mail.ci", "abidjan")).getId();
		ClientSearchCriteria abidjan = new ClientSearchCriteria("Abidjan", null, null);

		assertThat(clientRepository.findBatch(abidjan, null, 1)).extracting(Client::getId).containsExactly(awa);
		assertThat(clientRepository.findBatch(abidjan, awa, 1)).extracting(Client::getId).containsExactly(moussa);
		assertThat(clientRepository.findBatch(abidjan, moussa, 1)).isEmpty();
	}

	@Test
	void searchesWithoutCaringAboutAccents() {
		clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Bouaké"));
		clientRepository.save(newClient("Moussa", "Kone", "moussa@mail.ci", "Abidjan"));

		assertThat(searchEmails(new ClientSearchCriteria(null, null, "kone")))
				.containsExactly("awa@mail.ci", "moussa@mail.ci");
		assertThat(searchEmails(new ClientSearchCriteria(null, null, "KONÉ")))
				.containsExactly("awa@mail.ci", "moussa@mail.ci");
		assertThat(searchEmails(new ClientSearchCriteria("bouake", null, null))).containsExactly("awa@mail.ci");
	}

	@Test
	void treatsLikeWildcardsLiterally() {
		clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		assertThat(searchEmails(new ClientSearchCriteria(null, null, "%"))).isEmpty();
		assertThat(searchEmails(new ClientSearchCriteria(null, null, "_"))).isEmpty();
	}

	@Test
	void aDeletedClientDisappearsButIsKeptInStorage() {
		Client client = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		clientRepository.delete(client);
		entityManager.clear();

		assertThat(clientRepository.findById(client.getId())).isEmpty();
		assertThat(clientRepository.existsByEmail("awa@mail.ci")).isFalse();
		assertThat(searchEmails(new ClientSearchCriteria(null, null, "awa"))).isEmpty();
		Object[] row = (Object[]) entityManager
				.createNativeQuery("select deleted_at is not null, deleted_by from client where id = :id")
				.setParameter("id", client.getId())
				.getSingleResult();
		assertThat(row).containsExactly(true, "anonymous");
	}

	@Test
	void theEmailOfADeletedClientCanBeReused() {
		Client first = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));
		clientRepository.delete(first);

		Client second = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));

		assertThat(second.getId()).isNotEqualTo(first.getId());
	}

	private List<String> searchEmails(ClientSearchCriteria criteria) {
		return clientRepository.search(criteria, PageRequest.of(0, 20, Sort.by("email")))
				.map(Client::getEmail)
				.getContent();
	}

	private static Client newClient(String firstName, String lastName, String email, String city) {
		return Client.create(firstName, lastName, email, "0701020304", city);
	}

}
