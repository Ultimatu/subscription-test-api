package ci.orange.subscription.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description OpenAPI servie sur /v3/api-docs et affichée par Swagger UI sur /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER = "bearerAuth";

	@Bean
	OpenAPI openApi(ObjectProvider<BuildProperties> buildProperties) {
		// Les informations de build n'existent qu'après un build Maven, pas lors d'un lancement
		// direct depuis l'IDE.
		String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
		return new OpenAPI()
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
						.description("Jeton obtenu par POST /api/auth/login")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER))
				.info(new Info()
				.title("Subscription API")
				.version(version)
				.description("""
						API REST de gestion des clients, du catalogue d'offres et des souscriptions.

						**Authentification** : obtenir un jeton par `POST /api/auth/login`, puis le saisir avec le \
						bouton « Authorize ». Comptes de démonstration : `admin` / `Admin@2026` (tous les droits) et \
						`conseiller` / `Conseiller@2026` (clients et souscriptions, sans suppression ni statistiques).

						Les erreurs suivent le format Problem Details (RFC 9457), complété par un `code` stable \
						en majuscules (ex. `CLIENT_EMAIL_ALREADY_USED`), un `timestamp` et le `requestId`.

						Chaque réponse porte un en-tête `X-Request-Id`, repris de la requête s'il est fourni \
						(alphanumérique et tirets, 64 caractères au plus) : il permet de retrouver la requête \
						dans les logs.

						La pagination commence à 0 (paramètres `page`, `size`, `sort`, taille maximale 100)."""));
	}

}
