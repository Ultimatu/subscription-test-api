package ci.orange.subscription.infrastructure.web;

import ci.orange.subscription.infrastructure.config.ClockConfig;
import ci.orange.subscription.infrastructure.security.SecurityConfig;
import ci.orange.subscription.infrastructure.web.error.ProblemDetailFactory;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Tranche web pour un controller, avec ce dont chaque test de controller a besoin : les règles de
 * sécurité, l'horloge et la fabrique des corps d'erreur. Les requêtes sont faites en tant
 * qu'administrateur : ces tests portent sur le comportement des endpoints, les droits par rôle sont
 * vérifiés par SecurityIntegrationTest. Les cas d'usage sont simulés avec @MockitoBean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({ SecurityConfig.class, ClockConfig.class, ProblemDetailFactory.class })
@WithMockUser(roles = "ADMIN")
public @interface WebAdapterTest {

	@AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
	Class<?>[] value() default {};

}
