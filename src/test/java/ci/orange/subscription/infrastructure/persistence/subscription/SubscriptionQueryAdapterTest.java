package ci.orange.subscription.infrastructure.persistence.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

/** Les statistiques d'Hibernate comptent les requêtes, pour vérifier qu'un lot d'export n'en fait qu'une. */
@PersistenceAdapterTest
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.generate_statistics=true",
		"logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN" })
class SubscriptionQueryAdapterTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private SubscriptionQueries queries;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private OfferRepository offerRepository;

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
	void readsAnExportBatchByBatchInTheOrderOfIdentifiers() {
		SubscriptionSearchCriteria all = criteria(null, null, null, null, null, null);

		List<SubscriptionView> first = queries.findBatch(all, null, 2);
		List<SubscriptionView> next = queries.findBatch(all, first.get(1).id(), 2);

		assertThat(first).extracting(SubscriptionView::reference).containsExactly("SUB-2099-800001", "SUB-2099-800002");
		assertThat(next).extracting(SubscriptionView::reference).containsExactly("SUB-2099-800003");
		assertThat(queries.findBatch(all, next.get(0).id(), 2)).isEmpty();
	}

	@Test
	void appliesTheSearchCriteriaToTheExport() {
		assertThat(queries.findBatch(criteria(null, "FTTH", null, null, null, null), null, 10))
				.extracting(SubscriptionView::reference)
				.containsExactly("SUB-2099-800001", "SUB-2099-800003");
	}

	@Test
	void readsAnExportBatchWithItsClientsAndOffersInASingleQuery() {
		Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<SubscriptionView> batch = queries.findBatch(criteria(null, null, null, null, null, null), null, 10);

		assertThat(batch).extracting(view -> view.client().lastName(), view -> view.offer().code())
				.containsExactly(tuple("Koné", "FTTH-100"), tuple("Koné", "SAT-20"), tuple("Traoré", "FTTH-30"));
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
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
		assertThat(searchReferences(criteria(null, null, "bouake", null, null, null)))
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
				clientId, offerRepository.findById(offerId).orElseThrow(), startDate, TODAY);
	}

}
