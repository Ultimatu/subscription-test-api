package ci.orange.subscription.infrastructure.persistence.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditEvent;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@PersistenceAdapterTest
class AuditTrailAdapterTest {

	private static final AuditSearchCriteria EVERYTHING = new AuditSearchCriteria(null, null, null, null, null, null);

	@Autowired
	private AuditTrail auditTrail;

	@Autowired
	private EntityManager entityManager;

	@AfterEach
	void clearRequestId() {
		MDC.clear();
	}

	@Test
	void recordsWhoDidWhatWithItsDetailsAndRequest() {
		MDC.put("requestId", "req-123");
		auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_UPDATED, AuditResource.CLIENT, 42L, 42L,
				"Client Awa Koné modifié", Map.of("city", Map.of("before", "Abidjan", "after", "Bouaké"))));
		entityManager.flush();
		entityManager.clear();

		AuditEvent event = latest(new AuditSearchCriteria(AuditResource.CLIENT, "42", null, null, null, null)).get(0);

		assertThat(event.action()).isEqualTo(AuditAction.CLIENT_UPDATED);
		assertThat(event.actor()).isEqualTo("anonymous");
		assertThat(event.summary()).isEqualTo("Client Awa Koné modifié");
		assertThat(event.details()).isEqualTo(Map.of("city", Map.of("before", "Abidjan", "after", "Bouaké")));
		assertThat(event.requestId()).isEqualTo("req-123");
		assertThat(event.occurredAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
	}

	@Test
	void keepsTheActorOfAScheduledTask() {
		auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_PURGED, AuditResource.CLIENT, "77", null,
				"Client n° 77 purgé", Map.of(), "system"));

		AuditEvent event = latest(new AuditSearchCriteria(null, "77", "system", AuditAction.CLIENT_PURGED, null,
				null)).get(0);

		assertThat(event.details()).isNull();
		assertThat(event.requestId()).isNull();
	}

	@Test
	void searchesByActionAndPeriodFromTheMostRecent() {
		auditTrail.record(new AuditTrail.Entry(AuditAction.OFFER_CREATED, AuditResource.OFFER, 1L, null, "Offre A créée",
				null));
		auditTrail.record(new AuditTrail.Entry(AuditAction.OFFER_DEACTIVATED, AuditResource.OFFER, 1L, null,
				"Offre A retirée du catalogue", null));
		Instant now = Instant.now();

		assertThat(latest(new AuditSearchCriteria(AuditResource.OFFER, "1", null, null, null, null)))
				.extracting(AuditEvent::action).startsWith(AuditAction.OFFER_DEACTIVATED, AuditAction.OFFER_CREATED);
		assertThat(auditTrail.search(new AuditSearchCriteria(null, "1", null, AuditAction.OFFER_CREATED, null, null),
				now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), PageRequest.of(0, 20)).getContent())
				.extracting(AuditEvent::summary).contains("Offre A créée");
		assertThat(auditTrail.search(EVERYTHING, now.plus(1, ChronoUnit.HOURS), null, PageRequest.of(0, 20))
				.getContent()).isEmpty();
	}

	@Test
	void erasesTheEventsOfAPurgedClient() {
		auditTrail.record(new AuditTrail.Entry(AuditAction.CLIENT_CREATED, AuditResource.CLIENT, 55L, 55L,
				"Client Moussa Traoré créé", Map.of("email", "moussa@mail.ci")));
		auditTrail.record(new AuditTrail.Entry(AuditAction.SUBSCRIPTION_CREATED, AuditResource.SUBSCRIPTION, 900L, 55L,
				"Souscription SUB-2099-000900 créée", null));

		auditTrail.eraseClient(55L);
		entityManager.clear();

		assertThat(latest(new AuditSearchCriteria(AuditResource.CLIENT, "55", null, null, null, null))).isEmpty();
		assertThat(latest(new AuditSearchCriteria(AuditResource.SUBSCRIPTION, "900", null, null, null, null)))
				.isEmpty();
	}

	private List<AuditEvent> latest(AuditSearchCriteria criteria) {
		return auditTrail.search(criteria, null, null,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredAt", "id"))).getContent();
	}

}
