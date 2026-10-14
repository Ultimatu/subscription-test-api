package ci.orange.subscription.infrastructure.persistence.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@PersistenceAdapterTest
class SubscriptionQueryAdapterTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private SubscriptionQueries queries;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private EntityManager entityManager;

	private Long awaId;
	private Long moussaId;
	private Subscription fibre;

	@BeforeEach
	void createSubscriptions() {
		awaId = clientRepository.save(Client.create("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan")).getId();
		moussaId = clientRepository.save(Client.create("Moussa", "Traoré", "moussa@mail.ci", "0501020304", "Bouaké"))
				.getId();
		fibre = subscriptionRepository.save(newSubscription(1, awaId, "FTTH-100", TODAY));
		subscriptionRepository.save(newSubscription(2, awaId, "SAT-20", TODAY.plusDays(10)));
		subscriptionRepository.save(newSubscription(3, moussaId, "FTTH-30", TODAY.plusDays(20)));
		fibre.activate();
		fibre = subscriptionRepository.save(fibre);
		entityManager.clear();
	}

	@Test
	void readsASubscriptionWithAnOverviewOfItsClientAndOffer() {
		SubscriptionView view = queries.findById(fibre.getId()).orElseThrow();

		assertThat(view.reference()).isEqualTo("SUB-2099-800001");
		assertThat(view.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(view.technology()).isEqualTo("FTTH");
		assertThat(view.client().lastName()).isEqualTo("Koné");
		assertThat(view.client().city()).isEqualTo("Abidjan");
		assertThat(view.offer().code()).isEqualTo("FTTH-100");
		assertThat(view.offer().monthlyPrice()).isEqualByComparingTo("25000");
		assertThat(view.audit().createdBy()).isEqualTo("anonymous");
	}

	@Test
	void findsASubscriptionByItsNd() {
		assertThat(queries.findByNd("2780000001")).map(SubscriptionView::id).contains(fibre.getId());
		assertThat(queries.findByNd("2799999999")).isEmpty();
	}

	@Test
	void returnsTheHistoryOldestFirst() {
		assertThat(queries.findHistory(fibre.getId())).extracting(StatusHistoryEntry::from, StatusHistoryEntry::to)
				.containsExactly(
						tuple(null, SubscriptionStatus.PENDING),
						tuple(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE));
	}

	@Test
	void searchesByStatusTechnologyCityNdAndStartDate() {
		assertThat(searchReferences(criteria(SubscriptionStatus.PENDING, null, null, null, null, null)))
				.containsExactly("SUB-2099-800002", "SUB-2099-800003");
		assertThat(searchReferences(criteria(null, "FTTH", null, null, null, null)))
				.containsExactly("SUB-2099-800001", "SUB-2099-800003");
		assertThat(searchReferences(criteria(null, null, "BOUAKÉ", null, null, null)))
				.containsExactly("SUB-2099-800003");
		assertThat(searchReferences(criteria(null, null, null, "2780000002", null, null)))
				.containsExactly("SUB-2099-800002");
		assertThat(searchReferences(criteria(null, null, null, null, TODAY.plusDays(10), TODAY.plusDays(20))))
				.containsExactly("SUB-2099-800002", "SUB-2099-800003");
		assertThat(searchReferences(criteria(null, "FTTH", "abidjan", null, null, TODAY)))
				.containsExactly("SUB-2099-800001");
	}

	@Test
	void listsTheSubscriptionsOfAClient() {
		assertThat(queries.findByClient(awaId, PageRequest.of(0, 20, Sort.by("reference")))
				.map(SubscriptionView::reference).getContent())
				.containsExactly("SUB-2099-800001", "SUB-2099-800002");
	}

	@Test
	void paginates() {
		var page = queries.findByClient(awaId, PageRequest.of(1, 1, Sort.by("reference")));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.map(SubscriptionView::reference).getContent()).containsExactly("SUB-2099-800002");
	}

	private List<String> searchReferences(SubscriptionSearchCriteria criteria) {
		return queries.search(criteria, PageRequest.of(0, 20, Sort.by("reference")))
				.map(SubscriptionView::reference)
				.getContent();
	}

	private static SubscriptionSearchCriteria criteria(SubscriptionStatus status, String technology, String city,
			String nd, LocalDate from, LocalDate to) {
		return new SubscriptionSearchCriteria(status, technology, city, nd, from, to);
	}

	private Subscription newSubscription(int number, Long clientId, String offerCode, LocalDate startDate) {
		Long offerId = entityManager.createQuery("select o.id from OfferEntity o where o.code = :code", Long.class)
				.setParameter("code", offerCode)
				.getSingleResult();
		return Subscription.create("SUB-2099-%06d".formatted(800000 + number), "27%08d".formatted(80000000 + number),
				clientId, offerId, startDate, TODAY);
	}

}
