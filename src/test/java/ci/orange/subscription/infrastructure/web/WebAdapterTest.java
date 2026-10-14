package ci.orange.subscription.infrastructure.web;

import ci.orange.subscription.infrastructure.config.ClockConfig;
import ci.orange.subscription.infrastructure.security.SecurityConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * Tranche web pour un controller, avec ce dont chaque test de controller a besoin : les règles de
 * sécurité et l'horloge qui horodate les erreurs. Les cas d'usage sont simulés avec @MockitoBean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({ SecurityConfig.class, ClockConfig.class })
public @interface WebAdapterTest {

	@AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
	Class<?>[] value() default {};

}
