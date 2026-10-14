package ci.orange.subscription.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Today" is the date in Côte d'Ivoire whatever the server time zone, and tests can freeze it by
 * providing another clock.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Africa/Abidjan"));
	}

}
