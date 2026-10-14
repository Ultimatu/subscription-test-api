package ci.orange.subscription.infrastructure.persistence;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Séparée de la classe principale pour que les tests de la couche web (@WebMvcTest) ne tentent pas
 * de démarrer l'audit JPA.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	static final String ANONYMOUS_AUDITOR = "anonymous";

	@Bean
	AuditorAware<String> auditorAware() {
		return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
				.filter(Authentication::isAuthenticated)
				.filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
				.map(Authentication::getName)
				.or(() -> Optional.of(ANONYMOUS_AUDITOR));
	}

	/** Les dates d'audit viennent de l'horloge de l'application, comme toutes les autres dates. */
	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(clock.instant());
	}

}
