package ci.orange.subscription.infrastructure.persistence.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.EnumSet;
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

	private void insert(String offerCode, SubscriptionStatus status) {
		sequence++;
		SubscriptionEntity subscription = new SubscriptionEntity();
		subscription.setReference("SUB-2026-%06d".formatted(sequence));
		subscription.setNd("27%08d".formatted(sequence));
		subscription.setClient(entityManager.getReference(ClientEntity.class, clientId));
		subscription.setOffer(entityManager
				.createQuery("select o from OfferEntity o where o.code = :code", OfferEntity.class)
				.setParameter("code", offerCode)
				.getSingleResult());
		subscription.setStatus(status);
		subscription.setStartDate(LocalDate.now());
		entityManager.persist(subscription);
		entityManager.flush();
	}

}
