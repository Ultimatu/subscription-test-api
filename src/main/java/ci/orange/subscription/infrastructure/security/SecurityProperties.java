package ci.orange.subscription.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriétés {@code app.security.*} de application.yml.
 *
 * @param enabled false pour ouvrir toute l'API sans jeton (démonstration, tests manuels)
 * @param jwt émission et vérification des jetons d'accès
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(@DefaultValue("true") boolean enabled, Jwt jwt) {

	/**
	 * @param issuer émetteur inscrit dans les jetons et exigé à leur vérification
	 * @param validity durée de validité d'un jeton
	 * @param secret clé HMAC d'au moins 256 bits, encodée en Base64
	 */
	public record Jwt(@DefaultValue("subscription-api") String issuer, @DefaultValue("1h") Duration validity,
			String secret) {
	}

}
