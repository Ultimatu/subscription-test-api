package ci.orange.subscription.infrastructure.web.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/**
 * Body of suspend and terminate: the reason is mandatory and kept in the history (rule 20).
 */
public record ReasonedTransitionRequest(
		@Schema(example = "1") @NotNull Long subscriptionId,
		@Schema(example = "Impayé") @NotBlank @Size(max = 500) String reason,
		@Schema(hidden = true) @Null(message = "est attribué par le serveur et n'est jamais modifiable") String nd) {
}
