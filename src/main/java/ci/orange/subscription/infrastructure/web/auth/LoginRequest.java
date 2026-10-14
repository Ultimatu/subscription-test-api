package ci.orange.subscription.infrastructure.web.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@Schema(example = "conseiller") @NotBlank String username,
		@Schema(example = "Conseiller@2026") @NotBlank String password) {

	/** Le mot de passe ne doit jamais apparaître dans un log ni dans un message d'erreur. */
	@Override
	public String toString() {
		return "LoginRequest[username=" + username + ", password=***]";
	}

}
