package ci.orange.subscription.infrastructure.persistence.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class SubscriptionPersistenceAdapterTest {

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private EntityManager entityManager;

	private Long clientId;

	private int sequence;

	@BeforeEach
	void createClient() {
		clientId = clientRepository.save(Client.create("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan"))
				.getId();
	}

	@Test
	void countsSubscriptionsByStatus() {
		insert("FTTH-100", SubscriptionStatus.ACTIVE);
		insert("SAT-20", SubscriptionStatus.ACTIVE);
		insert("BOX4G-50", SubscriptionStatus.TERMINATED);

		assertThat(subscriptionRepository.countByStatusForClient(clientId))
				.isEqualTo(Map.of(SubscriptionStatus.ACTIVE, 2L, SubscriptionStatus.TERMINATED, 1L));
	}

	@Test
	void sumsTheMonthlyPriceOfSubscriptionsInAStatus() {
		insert("FTTH-100", SubscriptionStatus.ACTIVE);
		insert("BOX4G-50", SubscriptionStatus.ACTIVE);
		insert("SAT-20", SubscriptionStatus.SUSPENDED);

		assertThat(subscriptionRepository.sumMonthlyPriceForClient(clientId, SubscriptionStatus.ACTIVE))
				.isEqualByComparingTo("37000");
		assertThat(subscriptionRepository.sumMonthlyPriceForClient(clientId, SubscriptionStatus.PENDING))
				.isEqualByComparingTo("0");
	}

	@Test
	void detectsSubscriptionsInGivenStatuses() {
		insert("FTTH-100", SubscriptionStatus.TERMINATED);

		assertThat(subscriptionRepository.existsByClientIdAndStatusIn(clientId,
				EnumSet.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE))).isFalse();
		assertThat(subscriptionRepository.existsByClientIdAndStatusIn(clientId,
				EnumSet.of(SubscriptionStatus.TERMINATED))).isTrue();
	}

	@Test
	void softDeletesEveryClientSubscription() {
		insert("FTTH-100", SubscriptionStatus.TERMINATED);
		insert("SAT-20", SubscriptionStatus.TERMINATED);

		subscriptionRepository.deleteAllByClientId(clientId);
		entityManager.clear();

		assertThat(subscriptionRepository.countByStatusForClient(clientId)).isEmpty();
		Number keptRows = (Number) entityManager
				.createNativeQuery("select count(*) from subscription where client_id = :id and deleted_at is not null")
				.setParameter("id", clientId)
				.getSingleResult();
		assertThat(keptRows.longValue()).isEqualTo(2);
	}

	@Test
	void savesANewSubscriptionWithTheTechnologyOfItsOfferAndItsHistory() {
		Long offerId = offerId("SAT-50");

		Subscription saved = subscriptionRepository.save(newSubscription(offerId, 900001));
		entityManager.clear();

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
		assertThat(saved.recordedStatusChanges()).isEmpty();
		Object[] row = (Object[]) entityManager
				.createNativeQuery("select technology, client_id, offer_id from subscription where id = :id")
				.setParameter("id", saved.getId())
				.getSingleResult();
		assertThat(row).containsExactly("SATELLITE", clientId, offerId);
		Object[] history = (Object[]) entityManager.createNativeQuery(
				"select from_status, to_status, changed_by from subscription_status_history where subscription_id = :id")
				.setParameter("id", saved.getId())
				.getSingleResult();
		assertThat(history).containsExactly(null, "PENDING", "anonymous");
	}

	@Test
	void detectsAnOngoingSubscriptionOnATechnology() {
		insert("FTTH-100", SubscriptionStatus.ACTIVE);
		insert("SAT-20", SubscriptionStatus.TERMINATED);

		assertThat(subscriptionRepository.existsOngoingForClientAndTechnology(clientId, "FTTH")).isTrue();
		assertThat(subscriptionRepository.existsOngoingForClientAndTechnology(clientId, "SATELLITE")).isFalse();
		assertThat(subscriptionRepository.existsOngoingForClientAndTechnology(clientId, "4G_BOX")).isFalse();
	}

	@Test
	void theDatabaseRefusesASecondOngoingSubscriptionOnTheSameTechnology() {
		insert("FTTH-30", SubscriptionStatus.PENDING);

		assertThatThrownBy(() -> subscriptionRepository.save(newSubscription(offerId("FTTH-1000"), 900002)))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_ALREADY_SUBSCRIBED);
	}

	@Test
	void aTerminatedSubscriptionFreesTheTechnology() {
		insert("FTTH-30", SubscriptionStatus.TERMINATED);

		assertThat(subscriptionRepository.save(newSubscription(offerId("FTTH-1000"), 900003)).getId()).isNotNull();
	}

	@Test
	void appendsEveryStatusChangeToTheHistory() {
		Subscription subscription = subscriptionRepository.save(newSubscription(offerId("FTTH-100"), 900004));
		subscription.activate();
		subscription = subscriptionRepository.save(subscription);
		subscription.suspend("Impayé");
		subscription = subscriptionRepository.save(subscription);
		subscription.reactivate();
		subscription = subscriptionRepository.save(subscription);
		subscription.suspend("Demande du client");
		subscriptionRepository.save(subscription);
		entityManager.clear();

		List<?> history = entityManager.createNativeQuery("""
				select from_status, to_status, reason from subscription_status_history
				where subscription_id = :id order by id
				""").setParameter("id", subscription.getId()).getResultList();
		assertThat(history).extracting(row -> Arrays.toString((Object[]) row)).containsExactly(
				"[null, PENDING, null]",
				"[PENDING, ACTIVE, null]",
				"[ACTIVE, SUSPENDED, Impayé]",
				"[SUSPENDED, ACTIVE, null]",
				"[ACTIVE, SUSPENDED, Demande du client]");
		assertThat(subscriptionRepository.findById(subscription.getId()))
				.map(Subscription::getStatus)
				.contains(SubscriptionStatus.SUSPENDED);
	}

	@Test
	void aDeletedSubscriptionDisappearsButIsKeptInStorage() {
		Subscription subscription = subscriptionRepository.save(newSubscription(offerId("SAT-20"), 900005));

		subscriptionRepository.delete(subscription);
		entityManager.clear();

		assertThat(subscriptionRepository.findById(subscription.getId())).isEmpty();
		Number keptRows = (Number) entityManager
				.createNativeQuery("select count(*) from subscription where id = :id and deleted_by = 'anonymous'")
				.setParameter("id", subscription.getId())
				.getSingleResult();
		assertThat(keptRows.longValue()).isEqualTo(1);
	}

	private Subscription newSubscription(Long offerId, int number) {
		return Subscription.create("SUB-2099-%06d".formatted(number), "27%08d".formatted(number), clientId, offerId,
				LocalDate.now(), LocalDate.now());
	}

	private Long offerId(String code) {
		return entityManager.createQuery("select o.id from OfferEntity o where o.code = :code", Long.class)
				.setParameter("code", code)
				.getSingleResult();
	}

	private void insert(String offerCode, SubscriptionStatus status) {
		sequence++;
		SubscriptionEntity subscription = new SubscriptionEntity();
		subscription.setReference("SUB-2026-%06d".formatted(sequence));
		subscription.setNd("27%08d".formatted(sequence));
		subscription.setClient(entityManager.getReference(ClientEntity.class, clientId));
		OfferEntity offer = entityManager
				.createQuery("select o from OfferEntity o where o.code = :code", OfferEntity.class)
				.setParameter("code", offerCode)
				.getSingleResult();
		subscription.setOffer(offer);
		subscription.setTechnology(offer.getTechnology());
		subscription.setStatus(status);
		subscription.setStartDate(LocalDate.now());
		entityManager.persist(subscription);
		entityManager.flush();
	}

}
