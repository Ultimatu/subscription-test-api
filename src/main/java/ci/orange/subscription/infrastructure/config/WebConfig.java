package ci.orange.subscription.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Préfixe les controllers de l'adaptateur web par le chemin de base de l'API, déclaré une seule
 * fois. Les controllers des bibliothèques (Swagger, Actuator) ne sont pas touchés. Versionner l'API
 * reviendrait à modifier cette seule valeur.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	public static final String API_BASE_PATH = "/api";

	private static final String WEB_ADAPTER_PACKAGE = "ci.orange.subscription.infrastructure.web";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_BASE_PATH, HandlerTypePredicate.forBasePackage(WEB_ADAPTER_PACKAGE));
	}

}
