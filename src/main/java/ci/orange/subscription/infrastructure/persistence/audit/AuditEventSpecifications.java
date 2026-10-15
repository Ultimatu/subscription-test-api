package ci.orange.subscription.infrastructure.persistence.audit;

import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.infrastructure.persistence.entity.AuditEventEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class AuditEventSpecifications {

	private AuditEventSpecifications() {
	}

	/** Période : {@code from} inclus, {@code to} exclu. */
	static Specification<AuditEventEntity> matching(AuditSearchCriteria criteria, Instant from, Instant to) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.resourceType() != null) {
				predicates.add(builder.equal(root.get("resourceType"), criteria.resourceType()));
			}
			if (hasText(criteria.resourceId())) {
				predicates.add(builder.equal(root.get("resourceId"), criteria.resourceId().trim()));
			}
			if (hasText(criteria.actor())) {
				predicates.add(builder.equal(root.get("actor"), criteria.actor().trim()));
			}
			if (criteria.action() != null) {
				predicates.add(builder.equal(root.get("action"), criteria.action()));
			}
			if (from != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
			}
			if (to != null) {
				predicates.add(builder.lessThan(root.get("occurredAt"), to));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
