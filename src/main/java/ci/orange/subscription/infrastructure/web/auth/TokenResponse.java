package ci.orange.subscription.infrastructure.web.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record TokenResponse(
		@Schema(description = "À transmettre dans l'en-tête Authorization: Bearer <accessToken>") String accessToken,
		@Schema(example = "Bearer") String tokenType,
		@Schema(description = "Durée de validité, en secondes", example = "3600") long expiresIn,
		@Schema(example = "[\"CONSEILLER\"]") List<String> roles) {
}
