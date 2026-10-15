package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.audit.AuditEvent;
import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.application.port.in.audit.AuditUseCases;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditService implements AuditUseCases {

	private final AuditTrail auditTrail;
	private final Clock clock;

	public AuditService(AuditTrail auditTrail, Clock clock) {
		this.auditTrail = auditTrail;
		this.clock = clock;
	}

	/** Les jours de la période sont ceux d'Abidjan : du début de {@code from} à la fin de {@code to}. */
	@Override
	public Page<AuditEvent> search(AuditSearchCriteria criteria, Pageable pageable) {
		if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
			throw new DomainValidationException(DomainErrorCode.INVALID_DATE_RANGE,
					"from (%s) doit être antérieure ou égale à to (%s)".formatted(criteria.from(), criteria.to()));
		}
		Instant from = criteria.from() == null ? null : criteria.from().atStartOfDay(clock.getZone()).toInstant();
		Instant to = criteria.to() == null ? null : criteria.to().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
		return auditTrail.search(criteria, from, to, pageable);
	}

}
