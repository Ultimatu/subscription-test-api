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
 * JPA slice running against the real PostgreSQL schema (Flyway migrations included), since the
 * adapters rely on PostgreSQL-specific constraints such as partial unique indexes. Each test is
 * rolled back.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JpaAuditingConfig.class, ClockConfig.class })
@ComponentScan("ci.orange.subscription.infrastructure.persistence")
public @interface PersistenceAdapterTest {
}
