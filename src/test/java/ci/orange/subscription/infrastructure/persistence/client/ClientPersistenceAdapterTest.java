package ci.orange.subscription.infrastructure.persistence.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@PersistenceAdapterTest
class ClientPersistenceAdapterTest {

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private NotificationOutbox notificationOutbox;

	@Test
	void listsAndDatesTheLogicallyDeletedClients() {
		Client awa = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));
		Client kept = clientRepository.save(newClient("Moussa", "Traoré", "moussa@mail.ci", "Bouaké"));

		clientRepository.delete(awa);
		entityManager.clear();

		Instant deletedAt = clientRepository.findDeletionDate(awa.getId()).orElseThrow();
		assertThat(clientRepository.findDeletionDate(kept.getId())).isEmpty();
		assertThat(clientRepository.findDeleted(PageRequest.of(0, 20)).getContent())
				.extracting(ClientRepository.Deleted::id, ClientRepository.Deleted::email, ClientRepository.Deleted::deletedBy)
				.contains(tuple(awa.getId(), "awa@mail.ci", "anonymous"));
		assertThat(clientRepository.findDeletedBefore(deletedAt.plusSeconds(1))).contains(awa.getId());
		assertThat(clientRepository.findDeletedBefore(deletedAt.minusSeconds(1))).doesNotContain(awa.getId());
	}

	@Test
	void deletesPermanentlyAClientWithItsSubscriptionsHistoryAndNotifications() {
		Client awa = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));
		Long offerId = entityManager.createQuery("select o.id from OfferEntity o where o.code = 'FTTH-100'", Long.class)
				.getSingleResult();
		Subscription subscription = subscriptionRepository.save(Subscription.create("SUB-2099-990101", "2799990101",
				awa.getId(), offerRepository.findById(offerId).orElseThrow(), LocalDate.now(), LocalDate.now()));
		subscription.activate();
		subscriptionRepository.save(subscription);
		notificationOutbox.enqueue(new NotificationOutbox.Request(subscription.getId(), SubscriptionTransition.ACTIVATE,
				"awa@mail.ci", null));
		subscriptionRepository.deleteAllByClientId(awa.getId());
		clientRepository.delete(awa);

		clientRepository.deletePermanently(awa.getId());

		for (String table : List.of("client where id", "subscription where client_id")) {
			assertThat(count("select count(*) from " + table + " = :id", awa.getId())).isZero();
		}
		assertThat(count("select count(*) from subscription_status_history where subscription_id = :id",
				subscription.getId())).isZero();
		assertThat(count("select count(*) from notification_outbox where subscription_id = :id",
				subscription.getId())).isZero();
	}

	@Test
	void translatesTheErrorsOfItsNativeQueriesIntoSpringExceptions() {
		Client awa = clientRepository.save(newClient("Awa", "Koné", "awa@mail.ci", "Abidjan"));
		// Une table inconnue de la purge référence le client : le supprimer viole une clé étrangère. Le
		// DDL de PostgreSQL est transactionnel : la table disparaît avec l'annulation du test.
		entityManager.createNativeQuery("create table purge_blocker (client_id bigint references client (id))")
				.executeUpdate();
		entityManager.createNativeQuery("insert into purge_blocker values (:id)").setParameter("id", awa.getId())
				.executeUpdate();

		// Sans @Repository, l'exception de Hibernate sortirait telle quelle et finirait en erreur 500
		assertThatThrownBy(() -> clientRepository.deletePermanently(awa.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

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

	private long count(String sql, Long id) {
		return ((Number) entityManager.createNativeQuery(sql).setParameter("id", id).getSingleResult()).longValue();
	}

}
