package ci.orange.subscription.infrastructure.web.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

/**
 * Corps de activate et reactivate. L'identifiant passe dans le corps, comme l'impose l'énoncé.
 */
public record TransitionRequest(
		@Schema(example = "1") @NotNull Long subscriptionId,
		@Schema(hidden = true) @Null(message = "est attribué par le serveur et n'est jamais modifiable") String nd) {
}
