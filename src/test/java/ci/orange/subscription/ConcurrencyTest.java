package ci.orange.subscription;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Requêtes simultanées sur un même client, de bout en bout : vrai serveur HTTP, vraie base, deux
 * requêtes en parallèle.
 * <p>
 * Lancer deux threads au même moment ne suffit pas à faire se croiser deux requêtes : la première
 * peut se terminer avant que la seconde ne commence, et le test passerait sans rien prouver. Le test
 * tient donc lui-même le verrou du client, lance les requêtes l'une après l'autre en attendant que
 * chacune soit bloquée dessus, puis le relâche : elles sont alors réellement concurrentes, et
 * PostgreSQL leur accorde le verrou dans l'ordre où elles l'ont attendu. Le résultat de chaque
 * requête est donc connu d'avance, à chaque exécution.
 * <p>
 * Les requêtes valident leurs données : le test ne peut pas s'exécuter dans une transaction annulée
 * à la fin. Il efface donc ce qu'il a créé, comme {@code NotificationFlowTest}.
 */
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SubscriptionConcurrencyTest {

	@LocalServerPort
	private int port;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	private RestClient api;
	private Long clientId;
	private Long offerId;
	private Long referenceCounterBefore;

	@BeforeEach
	void createAClientAsAnAdministrator() {
		referenceCounterBefore = jdbcTemplate.query(
				"select last_value from subscription_reference_counter where year = ?",
				rs -> rs.next() ? rs.getLong(1) : null, today().getYear());
		RestClient anonymous = RestClient.builder()
				.baseUrl("http://localhost:" + port + "/api")
				.requestFactory(new JdkClientHttpRequestFactory(
						HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
				.build();
		String token = JsonPath.read(anonymous.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("username", "admin", "password", "Admin@2026")).retrieve().body(String.class),
				"$.accessToken");
		api = anonymous.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();

		Response client = post("/clients", Map.of("firstName", "Awa", "lastName", "Koné", "email",
				"concurrence." + System.nanoTime() + "@exemple.ci", "phoneNumberClient", "0701020304", "city",
				"Abidjan"));
		assertThat(client.status()).isEqualTo(201);
		clientId = client.id();
		offerId = jdbcTemplate.queryForObject("select id from offer where code = 'FTTH-100'", Long.class);
	}

	@AfterEach
	void eraseWhatThisTestCreated() {
		if (clientId != null) {
			String ofTheClient = " where subscription_id in (select id from subscription where client_id = ?)";
			jdbcTemplate.update("delete from notification_outbox" + ofTheClient, clientId);
			jdbcTemplate.update("delete from subscription_status_history" + ofTheClient, clientId);
			jdbcTemplate.update("delete from subscription where client_id = ?", clientId);
			jdbcTemplate.update("delete from client where id = ?", clientId);
			jdbcTemplate.update("delete from audit_event where client_id = ?", clientId);
			// Trace anonyme laissée par une suppression définitive
			jdbcTemplate.update("delete from audit_event where resource_type = 'CLIENT' and resource_id = ?",
					clientId.toString());
		}
		int year = today().getYear();
		if (referenceCounterBefore == null) {
			jdbcTemplate.update("delete from subscription_reference_counter where year = ?", year);
		}
		else {
			jdbcTemplate.update("update subscription_reference_counter set last_value = ? where year = ?",
					referenceCounterBefore, year);
		}
	}

	@Test
	void twoSimultaneousSubscriptionsToTheSameTechnologyGiveOneCreationAndOneConflict() throws Exception {
		Map<String, Object> request = Map.of("clientId", clientId, "offerId", offerId, "startDate",
				today().toString());

		List<Response> responses = whileTheClientIsLocked(() -> post("/subscriptions", request),
				() -> post("/subscriptions", request));

		assertThat(responses).extracting(Response::status).containsExactly(201, 409);
		assertThat(responses.get(1).code()).isEqualTo("TECHNOLOGY_ALREADY_SUBSCRIBED");
		assertThat(jdbcTemplate.queryForObject("select count(*) from subscription where client_id = ?", Long.class,
				clientId)).isOne();
	}

	/**
	 * La seconde résiliation lit la souscription une fois la première validée : le domaine la refuse
	 * comme si les deux requêtes s'étaient suivies, et non le verrou optimiste (CONCURRENT_MODIFICATION).
	 */
	@Test
	void twoSimultaneousTerminationsGiveOneTerminationAndOneInvalidTransition() throws Exception {
		Response created = post("/subscriptions", Map.of("clientId", clientId, "offerId", offerId, "startDate",
				today().toString()));
		assertThat(created.status()).isEqualTo(201);
		Map<String, Object> target = Map.of("subscriptionId", created.id());
		assertThat(post("/subscriptions/activate", target).status()).isEqualTo(200);
		Map<String, Object> request = Map.of("subscriptionId", created.id(), "reason", "Déménagement");

		List<Response> responses = whileTheClientIsLocked(() -> post("/subscriptions/terminate", request),
				() -> post("/subscriptions/terminate", request));

		assertThat(responses).extracting(Response::status).containsExactly(200, 409);
		Response rejected = responses.get(1);
		assertThat(rejected.code()).isEqualTo("INVALID_STATUS_TRANSITION");
		assertThat((String) JsonPath.read(rejected.body(), "$.currentStatus")).isEqualTo("TERMINATED");
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from subscription_status_history where subscription_id = ? and to_status = 'TERMINATED'",
				Long.class, created.id())).isOne();
	}

	/**
	 * Règle 6 : la suppression vérifie l'absence de souscription en cours une fois le verrou obtenu,
	 * donc après la création. Sans ce verrou, elle aurait fait sa vérification avant, puis échoué sur
	 * la clé étrangère de la nouvelle souscription, au lieu d'un refus métier.
	 */
	@Test
	void aClientDeletedWhileASubscriptionIsCreatedIsKeptWithItsSubscription() throws Exception {
		List<Response> responses = whileTheClientIsLocked(
				() -> post("/subscriptions", Map.of("clientId", clientId, "offerId", offerId, "startDate",
						today().toString())),
				() -> delete("/clients/" + clientId));

		assertThat(responses).extracting(Response::status).containsExactly(201, 409);
		assertThat(responses.get(1).code()).isEqualTo("CLIENT_HAS_ONGOING_SUBSCRIPTIONS");
		assertThat(jdbcTemplate.queryForObject("select count(*) from client where id = ? and deleted_at is null",
				Long.class, clientId)).isOne();
	}

	/**
	 * Exécute les requêtes en parallèle pendant que le test tient le verrou du client : chacune n'est
	 * lancée qu'une fois la précédente bloquée dessus, et le verrou n'est relâché qu'une fois toutes
	 * bloquées. Les réponses sont rendues dans l'ordre des requêtes.
	 */
	@SafeVarargs
	private List<Response> whileTheClientIsLocked(Callable<Response>... requests) throws Exception {
		ExecutorService threads = Executors.newFixedThreadPool(requests.length);
		try (Connection lock = dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try (PreparedStatement select = lock.prepareStatement("select id from client where id = ? for update")) {
				select.setLong(1, clientId);
				select.executeQuery();
			}
			List<Future<Response>> pending = new ArrayList<>();
			for (Callable<Response> request : requests) {
				pending.add(threads.submit(request));
				awaitRequestsWaitingForTheClient(pending.size());
			}
			lock.commit();
			List<Response> responses = new ArrayList<>();
			for (Future<Response> response : pending) {
				responses.add(response.get(10, SECONDS));
			}
			return responses;
		}
		finally {
			threads.shutdownNow();
		}
	}

	private void awaitRequestsWaitingForTheClient(int expected) throws InterruptedException {
		long deadline = System.nanoTime() + SECONDS.toNanos(10);
		while (requestsWaitingForTheClient() < expected) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError(expected + " requête(s) devaient attendre le verrou du client");
			}
			Thread.sleep(20);
		}
	}

	/**
	 * Connexions bloquées sur un verrou de la table client : le SELECT ... FOR UPDATE d'une écriture,
	 * ou toute autre requête qui en a besoin.
	 */
	private int requestsWaitingForTheClient() {
		return jdbcTemplate.queryForObject("""
				select count(*) from pg_stat_activity
				where datname = current_database() and wait_event_type = 'Lock' and query ilike '%from client %'
				""", Integer.class);
	}

	private Response post(String path, Map<String, Object> body) {
		return api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
				.exchange((request, response) -> new Response(response.getStatusCode().value(),
						response.bodyTo(String.class)));
	}

	private Response delete(String path) {
		return api.delete().uri(path).exchange((request, response) -> new Response(
				response.getStatusCode().value(), response.bodyTo(String.class)));
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private record Response(int status, String body) {

		Long id() {
			return ((Number) JsonPath.read(body, "$.id")).longValue();
		}

		String code() {
			return JsonPath.read(body, "$.code");
		}

	}

}
