package ci.orange.subscription.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefixes the controllers of the web adapter with the base path of the API, declared once.
 * Controllers of libraries (Swagger, Actuator) are left untouched. Versioning the API would only
 * mean changing this value.
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
