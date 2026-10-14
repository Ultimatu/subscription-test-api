package ci.orange.subscription.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at /v3/api-docs and rendered by Swagger UI at /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI openApi(ObjectProvider<BuildProperties> buildProperties) {
		// Build information only exists when built by Maven, not when run directly from the IDE.
		String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
		return new OpenAPI().info(new Info()
				.title("Subscription API")
				.version(version)
				.description("""
						API REST de gestion des clients, du catalogue d'offres et des souscriptions.

						Les erreurs suivent le format Problem Details (RFC 9457), complété par un `code` stable \
						en majuscules (ex. `CLIENT_EMAIL_ALREADY_USED`), un `timestamp` et le `requestId`.

						Chaque réponse porte un en-tête `X-Request-Id`, repris de la requête s'il est fourni \
						(alphanumérique et tirets, 64 caractères au plus) : il permet de retrouver la requête dans les logs.

						La pagination commence à 0 (paramètres `page`, `size`, `sort`, taille maximale 100)."""));
	}

}
