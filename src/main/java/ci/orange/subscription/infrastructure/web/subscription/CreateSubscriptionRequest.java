package ci.orange.subscription.infrastructure.web.subscription;

import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.LocalDate;

/**
 * {@code reference} et {@code nd} ne sont déclarés que pour refuser toute tentative de les choisir
 * : le serveur les attribue (règles 16 à 18).
 */
public record CreateSubscriptionRequest(
		@Schema(example = "1") @NotNull Long clientId,
		@Schema(example = "2") @NotNull Long offerId,
		@Schema(description = "Aujourd'hui ou plus tard", example = "2026-10-20") @NotNull LocalDate startDate,
		@Schema(hidden = true) @Null(message = "est attribuée par le serveur") String reference,
		@Schema(hidden = true) @Null(message = "est attribué par le serveur et n'est jamais modifiable") String nd) {

	CreateSubscriptionCommand toCommand() {
		return new CreateSubscriptionCommand(clientId, offerId, startDate);
	}

}
