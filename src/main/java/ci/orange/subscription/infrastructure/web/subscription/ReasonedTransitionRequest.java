package ci.orange.subscription.infrastructure.web.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * Corps de suspend et terminate : le motif est obligatoire et conservé dans l'historique (règle
 * 20).
 */
public record ReasonedTransitionRequest(
		@Schema(example = "1") @NotNull Long subscriptionId,
		@Schema(example = "Impayé") @NotBlank @Size(max = 500) String reason,
		@Schema(hidden = true) @Null(message = "est attribué par le serveur et n'est jamais modifiable") String nd) {
}
