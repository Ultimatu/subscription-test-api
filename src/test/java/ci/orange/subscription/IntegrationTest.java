package ci.orange.subscription;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.annotation.AliasFor;

/**
 * Test sur l'application complète et la vraie base : {@code @SpringBootTest}, avec l'étiquette
 * {@value #TAG} qui le distingue des tests unitaires. {@code mvn test -DexcludedGroups=integration}
 * n'exécute ainsi que les tests unitaires, pour en mesurer la couverture (ADR-0010).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Tag(IntegrationTest.TAG)
public @interface IntegrationTest {

	/** Étiquette de tous les tests qui démarrent la base de données. */
	String TAG = "integration";

	@AliasFor(annotation = SpringBootTest.class)
	String[] properties() default {};

	@AliasFor(annotation = SpringBootTest.class)
	WebEnvironment webEnvironment() default WebEnvironment.MOCK;

}
