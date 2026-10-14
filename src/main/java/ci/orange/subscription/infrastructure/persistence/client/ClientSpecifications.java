package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.infrastructure.persistence.TextSearch;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * La ville et le texte recherché sont comparés sans tenir compte de la casse ni des accents.
 */
final class ClientSpecifications {

	private ClientSpecifications() {
	}

	static Specification<ClientEntity> matching(ClientSearchCriteria criteria) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (hasText(criteria.city())) {
				predicates.add(TextSearch.equal(builder, root.get("city"), criteria.city()));
			}
			if (criteria.status() != null) {
				predicates.add(builder.equal(root.get("status"), criteria.status()));
			}
			if (hasText(criteria.search())) {
				predicates.add(builder.or(
						TextSearch.contains(builder, root.get("firstName"), criteria.search()),
						TextSearch.contains(builder, root.get("lastName"), criteria.search()),
						TextSearch.contains(builder, root.get("email"), criteria.search())));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
