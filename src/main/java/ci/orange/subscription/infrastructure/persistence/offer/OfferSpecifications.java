package ci.orange.subscription.infrastructure.persistence.offer;

import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class OfferSpecifications {

	private OfferSpecifications() {
	}

	static Specification<OfferEntity> matching(OfferSearchCriteria criteria) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.technology() != null && !criteria.technology().isBlank()) {
				predicates.add(builder.equal(root.get("technology"), criteria.technology().trim()));
			}
			if (criteria.active() != null) {
				predicates.add(builder.equal(root.get("active"), criteria.active()));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

}
