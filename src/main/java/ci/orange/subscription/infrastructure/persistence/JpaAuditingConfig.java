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
 * Kept out of the main application class so that web slice tests (@WebMvcTest) do not try to
 * bootstrap JPA auditing.
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

	/** Audit dates come from the application clock, like every other date of the application. */
	@Bean
	DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(clock.instant());
	}

}
