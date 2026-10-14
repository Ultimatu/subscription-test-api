package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class ClientSpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private ClientSpecifications() {
	}

	static Specification<ClientEntity> matching(ClientSearchCriteria criteria) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (hasText(criteria.city())) {
				predicates.add(builder.equal(builder.lower(root.get("city")), normalize(criteria.city())));
			}
			if (criteria.status() != null) {
				predicates.add(builder.equal(root.get("status"), criteria.status()));
			}
			if (hasText(criteria.search())) {
				String pattern = "%" + escapeLikeWildcards(normalize(criteria.search())) + "%";
				predicates.add(builder.or(
						builder.like(builder.lower(root.get("firstName")), pattern, LIKE_ESCAPE),
						builder.like(builder.lower(root.get("lastName")), pattern, LIKE_ESCAPE),
						builder.like(builder.lower(root.get("email")), pattern, LIKE_ESCAPE)));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	/** « % » et « _ » saisis par l'utilisateur sont recherchés tels quels, sans servir de jokers. */
	private static String escapeLikeWildcards(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
