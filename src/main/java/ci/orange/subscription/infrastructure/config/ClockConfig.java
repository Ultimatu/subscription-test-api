package ci.orange.subscription.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * « Aujourd'hui » est la date en Côte d'Ivoire, quel que soit le fuseau horaire du serveur, et les
 * tests peuvent la figer en fournissant une autre horloge.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("Africa/Abidjan"));
	}

}
