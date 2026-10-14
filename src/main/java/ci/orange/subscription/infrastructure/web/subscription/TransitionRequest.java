package ci.orange.subscription.infrastructure.web.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

/**
 * Body of activate and reactivate. The id travels in the body, as the specification imposes.
 */
public record TransitionRequest(
		@Schema(example = "1") @NotNull Long subscriptionId,
		@Schema(hidden = true) @Null(message = "est attribué par le serveur et n'est jamais modifiable") String nd) {
}
