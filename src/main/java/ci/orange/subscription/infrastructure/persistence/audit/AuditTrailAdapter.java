package ci.orange.subscription.infrastructure.persistence.audit;

import ci.orange.subscription.application.port.in.audit.AuditEvent;
import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.infrastructure.persistence.entity.AuditEventEntity;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuditTrailAdapter implements AuditTrail {

	/** Clé sous laquelle RequestTracingFilter place l'identifiant de la requête dans le MDC. */
	private static final String REQUEST_ID_MDC_KEY = "requestId";

	private final AuditEventJpaRepository jpaRepository;
	private final AuditorAware<String> auditorAware;
	private final Clock clock;

	AuditTrailAdapter(AuditEventJpaRepository jpaRepository, AuditorAware<String> auditorAware, Clock clock) {
		this.jpaRepository = jpaRepository;
		this.auditorAware = auditorAware;
		this.clock = clock;
	}

	/** Rejoint la transaction de l'action ; en crée une pour un export, qui n'en a pas. */
	@Override
	@Transactional
	public void record(Entry entry) {
		AuditEventEntity event = new AuditEventEntity();
		event.setOccurredAt(clock.instant());
		event.setActor(entry.actor() != null ? entry.actor() : auditorAware.getCurrentAuditor().orElseThrow());
		event.setAction(entry.action());
		event.setResourceType(entry.resourceType());
		event.setResourceId(entry.resourceId());
		event.setClientId(entry.clientId());
		event.setSummary(entry.summary());
		event.setDetails(entry.details() == null || entry.details().isEmpty() ? null : entry.details());
		event.setRequestId(MDC.get(REQUEST_ID_MDC_KEY));
		jpaRepository.save(event);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<AuditEvent> search(AuditSearchCriteria criteria, Instant from, Instant to, Pageable pageable) {
		return jpaRepository.findAll(AuditEventSpecifications.matching(criteria, from, to), pageable)
				.map(AuditTrailAdapter::toEvent);
	}

	@Override
	@Transactional
	public void eraseClient(Long clientId) {
		jpaRepository.deleteByClientId(clientId);
	}

	private static AuditEvent toEvent(AuditEventEntity entity) {
		return new AuditEvent(entity.getId(), entity.getOccurredAt(), entity.getActor(), entity.getAction(),
				entity.getResourceType(), entity.getResourceId(), entity.getSummary(), entity.getDetails(),
				entity.getRequestId());
	}

}
