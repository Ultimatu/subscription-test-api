package ci.orange.subscription.infrastructure.persistence;

import ci.orange.subscription.infrastructure.config.ClockConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Tranche JPA exécutée sur le vrai schéma PostgreSQL (migrations Flyway comprises), car les
 * adaptateurs s'appuient sur des contraintes propres à PostgreSQL, comme les index uniques
 * partiels. Chaque test est annulé à la fin.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JpaAuditingConfig.class, ClockConfig.class })
@ComponentScan("ci.orange.subscription.infrastructure.persistence")
public @interface PersistenceAdapterTest {
}
