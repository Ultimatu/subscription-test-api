package ci.orange.subscription.infrastructure.web.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Format de pagination stable de l'API, plutôt que de sérialiser tel quel le Page de Spring Data.
 * {@code page} commence à 0.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

}
