package ci.orange.subscription.infrastructure.persistence.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.StatisticsQueries;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class StatisticsQueryAdapterTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private StatisticsQueries statistics;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private EntityManager entityManager;

	private int number;

	/**
	 * Abidjan (deux clients, casse différente) : FTTH-100 activée, SAT-20 activée, BOX4G-50 en
	 * attente. Bouaké : FTTH-30 activée puis suspendue, SAT-50 en attente puis supprimée.
	 */
	@BeforeEach
	void createSubscriptions() {
		Long awa = client("awa@mail.ci", "Abidjan");
		Long yao = client("yao@mail.ci", "abidjan");
		Long moussa = client("moussa@mail.ci", "Bouaké");
		active(subscribe(awa, "FTTH-100", TODAY));
		active(subscribe(yao, "SAT-20", TODAY.plusDays(5)));
		subscribe(awa, "BOX4G-50", TODAY.plusDays(40));
		Subscription suspended = active(subscribe(moussa, "FTTH-30", TODAY));
		suspended.suspend("Impayé");
		subscriptionRepository.save(suspended);
		subscriptionRepository.delete(subscribe(moussa, "SAT-50", TODAY));
		entityManager.clear();
	}

	@Test
	void groupsActiveSubscriptionsByCityWhateverTheCase() {
		assertThat(statistics.activeSubscriptionsByCity()).singleElement().satisfies(city -> {
			assertThat(city.city()).isEqualToIgnoringCase("abidjan");
			assertThat(city.activeSubscriptions()).isEqualTo(2);
			assertThat(city.monthlyRevenue()).isEqualByComparingTo("60000");
		});
	}

	@Test
	void countsActiveSubscriptionsByTechnology() {
		assertThat(statistics.activeSubscriptionsByTechnology()).isEqualTo(Map.of("FTTH", 1L, "SATELLITE", 1L));
	}

	@Test
	void countsSubscriptionsCreatedInAPeriodByCurrentStatus() {
		Instant now = Instant.now();

		assertThat(statistics.countByCurrentStatusCreatedBetween(now.minus(1, ChronoUnit.HOURS),
				now.plus(1, ChronoUnit.HOURS)))
				.isEqualTo(Map.of(SubscriptionStatus.ACTIVE, 2L, SubscriptionStatus.PENDING, 1L,
						SubscriptionStatus.SUSPENDED, 1L));
		assertThat(statistics.countByCurrentStatusCreatedBetween(null, now.minus(1, ChronoUnit.DAYS))).isEmpty();
	}

	@Test
	void countsSubscriptionsStartingInAPeriodByCurrentStatus() {
		assertThat(statistics.countByCurrentStatusStartingBetween(TODAY.plusDays(1), TODAY.plusDays(30)))
				.isEqualTo(Map.of(SubscriptionStatus.ACTIVE, 1L));
		assertThat(statistics.countByCurrentStatusStartingBetween(TODAY.plusDays(40), null))
				.isEqualTo(Map.of(SubscriptionStatus.PENDING, 1L));
	}

	@Test
	void countsSubscriptionsThatEnteredEachStatusInAPeriod() {
		Instant now = Instant.now();
		Instant from = now.minus(1, ChronoUnit.HOURS);
		Instant to = now.plus(1, ChronoUnit.HOURS);

		assertThat(statistics.countByStatusEnteredBetween(from, to)).isEqualTo(Map.of(
				SubscriptionStatus.PENDING, 4L,
				SubscriptionStatus.ACTIVE, 3L,
				SubscriptionStatus.SUSPENDED, 1L));
		assertThat(statistics.countChangedBetween(from, to)).isEqualTo(4);
	}

	private Long client(String email, String city) {
		return clientRepository.save(Client.create("Prénom", "Nom", email, "0701020304", city)).getId();
	}

	private Subscription subscribe(Long clientId, String offerCode, LocalDate startDate) {
		number++;
		Long offerId = entityManager.createQuery("select o.id from OfferEntity o where o.code = :code", Long.class)
				.setParameter("code", offerCode)
				.getSingleResult();
		return subscriptionRepository.save(Subscription.create("SUB-2099-%06d".formatted(700000 + number),
				"27%08d".formatted(70000000 + number), clientId, offerId, startDate, TODAY));
	}

	private Subscription active(Subscription subscription) {
		subscription.activate();
		return subscriptionRepository.save(subscription);
	}

}
