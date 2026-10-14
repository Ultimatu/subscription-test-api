package ci.orange.subscription.infrastructure.web.offer;

import ci.orange.subscription.application.port.in.offer.UpdateOfferCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code code}, {@code technology} et {@code active} ne sont déclarés que pour refuser toute
 * tentative de les modifier ici : les deux premiers ne changent jamais (règle 10), la désactivation
 * passe par PATCH (règle 9).
 */
public record UpdateOfferRequest(
		@Schema(example = "Fibre Confort Plus 100 Mbps") @NotBlank @Size(max = 255) String label,
		@Schema(example = "27000") @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal monthlyPrice,
		@Schema(hidden = true) @Null(message = "n'est pas modifiable après la création de l'offre") String code,
		@Schema(hidden = true) @Null(message = "n'est pas modifiable après la création de l'offre") String technology,
		@Schema(hidden = true) @Null(message = "ne se modifie que par PATCH /api/offers/{id}/deactivate") Boolean active) {

	UpdateOfferCommand toCommand() {
		return new UpdateOfferCommand(label, monthlyPrice);
	}

}
