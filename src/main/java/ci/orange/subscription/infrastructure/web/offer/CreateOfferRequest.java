package ci.orange.subscription.infrastructure.web.offer;

import ci.orange.subscription.application.port.in.offer.CreateOfferCommand;
import ci.orange.subscription.domain.offer.Offer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An "active" flag sent in the body is simply ignored: a new offer is always active (rule 9).
 */
public record CreateOfferRequest(
		@Schema(example = "FTTH-2000") @NotBlank @Size(max = 50) @Pattern(regexp = Offer.CODE_REGEX,
				message = "doit être en majuscules (lettres, chiffres et tirets), ex. FTTH-100") String code,
		@Schema(example = "Fibre Ultra 2 Gbps") @NotBlank @Size(max = 255) String label,
		@Schema(description = "Code d'une technologie du catalogue (GET /api/technologies)", example = "FTTH")
		@NotBlank @Size(max = 20) String technology,
		@Schema(example = "200000") @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal monthlyPrice) {

	CreateOfferCommand toCommand() {
		return new CreateOfferCommand(code, label, technology, monthlyPrice);
	}

}
