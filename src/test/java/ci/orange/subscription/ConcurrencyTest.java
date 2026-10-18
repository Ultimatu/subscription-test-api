package ci.orange.subscription;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.domain.subscription.SubscriptionNumbering;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

/**
 * Requêtes simultanées, de bout en bout : vrai serveur HTTP, vraie base, une requête par thread.
 * <p>
 * Lancer deux threads au même moment ne suffit pas à faire se croiser deux requêtes : la première
 * peut se terminer avant que la seconde ne commence, et le test passerait sans rien prouver. Le test
 * prend donc lui-même, sur sa propre connexion, le verrou que les requêtes vont rencontrer, les lance
 * l'une après l'autre en attendant (via {@code pg_stat_activity}) que chacune soit bloquée sur
 * l'instruction SQL attendue, puis le relâche : elles sont alors réellement concurrentes, et
 * PostgreSQL leur accorde le verrou dans l'ordre où elles l'ont attendu. Le résultat de chaque
 * requête est donc connu d'avance, à chaque exécution.
 * <p>
 * Deux familles de cas :
 * <ul>
 * <li>les écritures sur les souscriptions d'un même client, que le verrou du client met l'une après
 * l'autre (ADR-0025) : elles reçoivent les erreurs métier de requêtes successives ;</li>
 * <li>les écritures que ce verrou ne couvre pas : l'index unique de l'email, le compteur de
 * références partagé par tous les clients, le verrou optimiste d'une modification, et la réservation
 * des notifications par plusieurs instances de l'application.</li>
 * </ul>
 * <p>
 * Les requêtes valident leurs données : le test ne peut pas s'exécuter dans une transaction annulée
 * à la fin. Il efface donc ce qu'il a créé, comme {@code NotificationFlowTest}.
 */
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConcurrencyTest {

	/** Le verrou que prend toute écriture sur les souscriptions d'un client. */
	private static final String LOCK_CLIENT = "select id from client where id = ? for update";

	@LocalServerPort
	private int port;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private NotificationOutbox notificationOutbox;

	@Autowired
	private Clock clock;

	private final List<Long> createdClients = new ArrayList<>();
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

		clientId = createClient();
		offerId = jdbcTemplate.queryForObject("select id from offer where code = 'FTTH-100'", Long.class);
	}

	@AfterEach
	void eraseWhatThisTestCreated() {
		String ofTheClient = " where subscription_id in (select id from subscription where client_id = ?)";
		for (Long id : createdClients) {
			jdbcTemplate.update("delete from notification_outbox" + ofTheClient, id);
			jdbcTemplate.update("delete from subscription_status_history" + ofTheClient, id);
			jdbcTemplate.update("delete from subscription where client_id = ?", id);
			jdbcTemplate.update("delete from client where id = ?", id);
			jdbcTemplate.update("delete from audit_event where client_id = ?", id);
			// Trace anonyme laissée par une suppression définitive
			jdbcTemplate.update("delete from audit_event where resource_type = 'CLIENT' and resource_id = ?",
					id.toString());
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
		List<Response> responses = whileTheClientIsLocked(() -> post("/subscriptions", subscriptionOf(clientId)),
				() -> post("/subscriptions", subscriptionOf(clientId)));

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
		Long subscriptionId = createActiveSubscription();
		Map<String, Object> request = Map.of("subscriptionId", subscriptionId, "reason", "Déménagement");

		List<Response> responses = whileTheClientIsLocked(() -> post("/subscriptions/terminate", request),
				() -> post("/subscriptions/terminate", request));

		assertThat(responses).extracting(Response::status).containsExactly(200, 409);
		Response rejected = responses.get(1);
		assertThat(rejected.code()).isEqualTo("INVALID_STATUS_TRANSITION");
		assertThat(rejected.text("$.currentStatus")).isEqualTo("TERMINATED");
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from subscription_status_history where subscription_id = ? and to_status = 'TERMINATED'",
				Long.class, subscriptionId)).isOne();
	}

	/**
	 * Règle 6 : la suppression vérifie l'absence de souscription en cours une fois le verrou obtenu,
	 * donc après la création. Sans ce verrou, elle aurait fait sa vérification avant, puis échoué sur
	 * la clé étrangère de la nouvelle souscription, au lieu d'un refus métier.
	 */
	@Test
	void aClientDeletedWhileASubscriptionIsCreatedIsKeptWithItsSubscription() throws Exception {
		List<Response> responses = whileTheClientIsLocked(() -> post("/subscriptions", subscriptionOf(clientId)),
				() -> delete("/clients/" + clientId));

		assertThat(responses).extracting(Response::status).containsExactly(201, 409);
		assertThat(responses.get(1).code()).isEqualTo("CLIENT_HAS_ONGOING_SUBSCRIPTIONS");
		assertThat(jdbcTemplate.queryForObject("select count(*) from client where id = ? and deleted_at is null",
				Long.class, clientId)).isOne();
	}

	/**
	 * Les deux créations doivent passer la vérification de l'email avant que l'une d'elles ne soit
	 * validée : le test tient une insertion du même email, non validée, invisible pour cette
	 * vérification, sur laquelle les deux insertions butent. Il l'annule ensuite : l'index unique
	 * tranche, et la perdante reçoit le refus métier au lieu d'une erreur technique. PostgreSQL réveille
	 * les deux requêtes en même temps : l'ordre de la gagnante n'est pas déterminé.
	 */
	@Test
	void twoSimultaneousCreationsWithTheSameEmailGiveOneClientAndOneConflict() throws Exception {
		String email = uniqueEmail();

		List<Response> responses = whileHolding("""
				insert into client (first_name, last_name, email, phone_number_client, city, status, version,
				                    created_at, updated_at, created_by, updated_by)
				values ('Verrou', 'Test', ?, '0701020304', 'Abidjan', 'ACTIVE', 0, now(), now(), 'test', 'test')
				""", email, "insert into client ", () -> post("/clients", newClient(email)),
				() -> post("/clients", newClient(email)));
		responses.stream().filter(response -> response.status() == 201)
				.forEach(response -> createdClients.add(response.id()));

		assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(201, 409);
		assertThat(responses).filteredOn(response -> response.status() == 409).extracting(Response::code)
				.containsExactly("CLIENT_EMAIL_ALREADY_USED");
		assertThat(jdbcTemplate.queryForObject("select count(*) from client where email = ?", Long.class, email))
				.isOne();
	}

	/**
	 * Deux clients différents ne se disputent pas leur verrou, mais partagent le compteur de références
	 * de l'année : le test tient sa ligne, les deux créations l'attendent, puis l'incrémentent l'une
	 * après l'autre. Aucune référence n'est attribuée deux fois.
	 */
	@Test
	void simultaneousSubscriptionsOfTwoClientsGetConsecutiveReferences() throws Exception {
		Long otherClientId = createClient();
		int year = today().getYear();
		jdbcTemplate.update("""
				insert into subscription_reference_counter (year, last_value) values (?, 0)
				on conflict (year) do nothing
				""", year);
		long lastReference = jdbcTemplate.queryForObject(
				"select last_value from subscription_reference_counter where year = ?", Long.class, year);

		List<Response> responses = whileHolding(
				"select year from subscription_reference_counter where year = ? for update", year,
				"insert into subscription_reference_counter", () -> post("/subscriptions", subscriptionOf(clientId)),
				() -> post("/subscriptions", subscriptionOf(otherClientId)));

		assertThat(responses).extracting(Response::status).containsExactly(201, 201);
		assertThat(responses).extracting(response -> response.text("$.reference")).containsExactly(
				SubscriptionNumbering.reference(year, lastReference + 1),
				SubscriptionNumbering.reference(year, lastReference + 2));
		assertThat(responses).extracting(response -> response.text("$.nd")).doesNotHaveDuplicates();
	}

	/**
	 * Modifier un client ne prend pas son verrou : les deux requêtes lisent la même version, puis
	 * attendent sur l'UPDATE que le test bloque. La seconde trouve une version qui a changé et échoue,
	 * au lieu d'écraser la première sans le savoir (ADR-0025).
	 */
	@Test
	void twoSimultaneousUpdatesOfAClientGiveOneUpdateAndOneConcurrentModification() throws Exception {
		List<Response> responses = whileHolding(LOCK_CLIENT, clientId, "update client ",
				() -> put("/clients/" + clientId, clientLivingIn("Bouaké")),
				() -> put("/clients/" + clientId, clientLivingIn("Yamoussoukro")));

		assertThat(responses).extracting(Response::status).containsExactly(200, 409);
		assertThat(responses.get(1).code()).isEqualTo("CONCURRENT_MODIFICATION");
		assertThat(jdbcTemplate.queryForObject("select city from client where id = ?", String.class, clientId))
				.isEqualTo("Bouaké");
	}

	/**
	 * Plusieurs instances de l'application réservent les notifications dues en même temps (ADR-0021).
	 * Le test joue l'instance A, qui tient une notification le temps de sa réservation ; celle de
	 * l'instance B, dans un autre thread, doit la sauter sans l'attendre et prendre la suivante. Sans
	 * {@code SKIP LOCKED}, elle resterait bloquée et le délai ferait échouer le test.
	 */
	@Test
	void aNotificationReservedByAnotherInstanceIsSkippedRatherThanAwaited() throws Exception {
		Long subscriptionId = createActiveSubscription();
		assertThat(post("/subscriptions/suspend", Map.of("subscriptionId", subscriptionId, "reason", "Impayé"))
				.status()).isEqualTo(200);
		List<Long> notifications = jdbcTemplate.queryForList(
				"select id from notification_outbox where subscription_id = ? order by id", Long.class, subscriptionId);
		assertThat(notifications).hasSize(2);
		Long reservedByA = notifications.get(0);
		Long free = notifications.get(1);

		try (Connection instanceA = dataSource.getConnection()) {
			instanceA.setAutoCommit(false);
			execute(instanceA, "select id from notification_outbox where id = ? for update", reservedByA);

			List<Long> claimedByB = assertTimeoutPreemptively(Duration.ofSeconds(5), this::claimDueNotifications);

			assertThat(claimedByB).contains(free).doesNotContain(reservedByA);
			instanceA.rollback();
		}
		// L'instance A a abandonné sans poser de bail : la notification redevient disponible
		assertThat(claimDueNotifications()).contains(reservedByA).doesNotContain(free);
	}

	@SafeVarargs
	private List<Response> whileTheClientIsLocked(Callable<Response>... requests) throws Exception {
		return whileHolding(LOCK_CLIENT, clientId, "from client ", requests);
	}

	/**
	 * Exécute les requêtes en parallèle pendant que le test tient un verrou, pris sur sa propre
	 * connexion par {@code lockingStatement} : chacune n'est lancée qu'une fois la précédente bloquée
	 * sur une instruction qui contient {@code blockedStatement}, et le verrou n'est relâché (transaction
	 * du test annulée) qu'une fois toutes bloquées. Les réponses sont rendues dans l'ordre des requêtes.
	 */
	@SafeVarargs
	private List<Response> whileHolding(String lockingStatement, Object parameter, String blockedStatement,
			Callable<Response>... requests) throws Exception {
		ExecutorService threads = Executors.newFixedThreadPool(requests.length);
		try (Connection lock = dataSource.getConnection()) {
			lock.setAutoCommit(false);
			execute(lock, lockingStatement, parameter);
			List<Future<Response>> pending = new ArrayList<>();
			for (Callable<Response> request : requests) {
				pending.add(threads.submit(request));
				awaitRequestsBlockedOn(blockedStatement, pending.size());
			}
			lock.rollback();
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

	private static void execute(Connection connection, String statement, Object parameter) throws SQLException {
		try (PreparedStatement prepared = connection.prepareStatement(statement)) {
			prepared.setObject(1, parameter);
			prepared.execute();
		}
	}

	private void awaitRequestsBlockedOn(String statement, int expected) throws InterruptedException {
		long deadline = System.nanoTime() + SECONDS.toNanos(10);
		while (requestsBlockedOn(statement) < expected) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError(expected + " requête(s) devaient attendre un verrou sur : " + statement);
			}
			Thread.sleep(20);
		}
	}

	/** Connexions de cette base en attente d'un verrou, sur une instruction qui contient le texte donné. */
	private int requestsBlockedOn(String statement) {
		return jdbcTemplate.queryForObject("""
				select count(*) from pg_stat_activity
				where datname = current_database() and wait_event_type = 'Lock' and query ilike ?
				""", Integer.class, "%" + statement + "%");
	}

	private List<Long> claimDueNotifications() {
		Instant now = clock.instant();
		return notificationOutbox.claimDue(now, now.plus(Duration.ofMinutes(5)), 100).stream()
				.map(NotificationOutbox.Pending::id)
				.toList();
	}

	private Long createClient() {
		Response client = post("/clients", newClient(uniqueEmail()));
		assertThat(client.status()).isEqualTo(201);
		createdClients.add(client.id());
		return client.id();
	}

	private Long createActiveSubscription() {
		Response created = post("/subscriptions", subscriptionOf(clientId));
		assertThat(created.status()).isEqualTo(201);
		assertThat(post("/subscriptions/activate", Map.of("subscriptionId", created.id())).status()).isEqualTo(200);
		return created.id();
	}

	private static String uniqueEmail() {
		return "concurrence." + System.nanoTime() + "@exemple.ci";
	}

	private static Map<String, Object> newClient(String email) {
		return Map.of("firstName", "Awa", "lastName", "Koné", "email", email, "phoneNumberClient", "0701020304",
				"city", "Abidjan");
	}

	private static Map<String, Object> clientLivingIn(String city) {
		return Map.of("firstName", "Awa", "lastName", "Koné", "phoneNumberClient", "0701020304", "city", city,
				"status", "ACTIVE");
	}

	private Map<String, Object> subscriptionOf(Long client) {
		return Map.of("clientId", client, "offerId", offerId, "startDate", today().toString());
	}

	private Response post(String path, Map<String, Object> body) {
		return api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
				.exchange(ConcurrencyTest::response);
	}

	private Response put(String path, Map<String, Object> body) {
		return api.put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
				.exchange(ConcurrencyTest::response);
	}

	private Response delete(String path) {
		return api.delete().uri(path).exchange(ConcurrencyTest::response);
	}

	private static Response response(HttpRequest request, ConvertibleClientHttpResponse response)
			throws IOException {
		return new Response(response.getStatusCode().value(), response.bodyTo(String.class));
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private record Response(int status, String body) {

		Long id() {
			return ((Number) JsonPath.read(body, "$.id")).longValue();
		}

		String code() {
			return text("$.code");
		}

		String text(String path) {
			return JsonPath.read(body, path);
		}

	}

}
