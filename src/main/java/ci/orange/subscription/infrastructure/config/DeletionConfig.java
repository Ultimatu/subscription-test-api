package ci.orange.subscription.infrastructure.config;

import ci.orange.subscription.application.service.DeletionPolicy;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Politique de suppression et de conservation (ADR-0023), lue dans {@code app.deletion.*}.
 */
@Configuration
@EnableConfigurationProperties(DeletionConfig.DeletionProperties.class)
public class DeletionConfig {

	@Bean
	DeletionPolicy deletionPolicy(DeletionProperties properties) {
		return new DeletionPolicy(properties.softDelete(), properties.hardDelete(), properties.retention().enabled(),
				properties.retention().period());
	}

	@ConfigurationProperties("app.deletion")
	record DeletionProperties(boolean softDelete, boolean hardDelete, Retention retention) {

		record Retention(boolean enabled, Period period, String purgeCron) {
		}

	}

}
