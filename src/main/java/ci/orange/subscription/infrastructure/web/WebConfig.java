package ci.orange.subscription.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefixes every REST controller with the base path of the API, declared once (Actuator is not
 * affected). Versioning the API would only mean changing this value.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	public static final String API_BASE_PATH = "/api";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_BASE_PATH, HandlerTypePredicate.forAnnotation(RestController.class));
	}

}
