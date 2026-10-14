package ci.orange.subscription.infrastructure.web.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Pour la documentation uniquement : la forme des corps d'erreur construits par
 * {@link GlobalExceptionHandler} à partir du ProblemDetail de Spring, dont OpenAPI ne peut pas
 * découvrir seul les propriétés supplémentaires.
 */
@Schema(name = "Problem",
		description = "Erreur au format Problem Details (RFC 9457), enrichie d'un code, d'un horodatage et de l'identifiant de la requête")
public record ApiProblem(
		@Schema(example = "about:blank") String type,
		@Schema(example = "Ressource introuvable") String title,
		@Schema(example = "404") int status,
		@Schema(example = "Client introuvable (id 99)") String detail,
		@Schema(example = "/api/clients/99") String instance,
		@Schema(description = "Code d'erreur stable, en majuscules, sur lequel un client de l'API peut s'appuyer",
				example = "CLIENT_NOT_FOUND") String code,
		@Schema(example = "2026-10-14T14:30:00Z") Instant timestamp,
		@Schema(description = "Identifiant de la requête (en-tête X-Request-Id), à communiquer au support",
				example = "eeffa598-b1f6-419e-9629-433e2b2c71cf") String requestId,
		@Schema(description = "Champs invalides, pour les erreurs de validation uniquement") List<InvalidField> errors) {
}
