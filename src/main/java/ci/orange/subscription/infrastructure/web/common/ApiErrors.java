package ci.orange.subscription.infrastructure.web.common;

import ci.orange.subscription.infrastructure.web.error.ApiProblem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Réponses d'erreur OpenAPI réutilisables, toutes documentées avec le corps Problem Details : les
 * controllers n'ont qu'à indiquer les erreurs qu'un endpoint peut renvoyer.
 */
public final class ApiErrors {

	private ApiErrors() {
	}

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@ApiResponse(responseCode = "400", description = "Requête invalide (champ, paramètre ou corps)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
	public @interface BadRequest {
	}

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@ApiResponse(responseCode = "401",
			description = "Jeton absent, invalide ou expiré (ou identifiants incorrects à la connexion)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
	public @interface Unauthorized {
	}

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@ApiResponse(responseCode = "403", description = "Rôle insuffisant pour cette opération",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
	public @interface Forbidden {
	}

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@ApiResponse(responseCode = "404", description = "Ressource introuvable",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
	public @interface NotFound {
	}

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@ApiResponse(responseCode = "409", description = "Règle métier non respectée",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
	public @interface Conflict {
	}

}
