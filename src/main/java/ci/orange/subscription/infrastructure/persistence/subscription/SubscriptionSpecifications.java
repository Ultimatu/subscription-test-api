package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.infrastructure.persistence.TextSearch;
import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class SubscriptionSpecifications {

	private SubscriptionSpecifications() {
	}

	/** La technologie est filtrée sur la copie portée par la souscription, sans jointure avec l'offre. */
	static Specification<SubscriptionEntity> matching(SubscriptionSearchCriteria criteria) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.status() != null) {
				predicates.add(builder.equal(root.get("status"), criteria.status()));
			}
			if (hasText(criteria.technology())) {
				predicates.add(builder.equal(root.get("technology"), criteria.technology().trim()));
			}
			if (hasText(criteria.city())) {
				predicates.add(TextSearch.equal(builder, root.get("client").get("city"), criteria.city()));
			}
			if (hasText(criteria.nd())) {
				predicates.add(builder.equal(root.get("nd"), criteria.nd().trim()));
			}
			if (criteria.startDateFrom() != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("startDate"), criteria.startDateFrom()));
			}
			if (criteria.startDateTo() != null) {
				predicates.add(builder.lessThanOrEqualTo(root.get("startDate"), criteria.startDateTo()));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	static Specification<SubscriptionEntity> ofClient(Long clientId) {
		return (root, query, builder) -> builder.equal(root.get("client").get("id"), clientId);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
