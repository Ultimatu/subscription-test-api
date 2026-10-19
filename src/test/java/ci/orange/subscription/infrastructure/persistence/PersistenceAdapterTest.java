package ci.orange.subscription.infrastructure.persistence;

import ci.orange.subscription.IntegrationTest;
import ci.orange.subscription.infrastructure.config.ClockConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Tranche JPA exécutée sur le vrai schéma PostgreSQL (migrations Flyway comprises), car les
 * adaptateurs s'appuient sur des contraintes propres à PostgreSQL, comme les index uniques
 * partiels. Chaque test est annulé à la fin. Comme tout test qui démarre la base, il porte
 * l'étiquette des tests d'intégration (ADR-0010). La traduction des exceptions des adaptateurs
 * {@code @Repository}, absente de la tranche {@code @DataJpaTest}, est ajoutée comme dans l'application.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@Tag(IntegrationTest.TAG)
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JpaAuditingConfig.class, ClockConfig.class })
@ComponentScan("ci.orange.subscription.infrastructure.persistence")
public @interface PersistenceAdapterTest {
}
