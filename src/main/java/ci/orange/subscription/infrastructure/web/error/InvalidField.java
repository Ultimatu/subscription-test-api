package ci.orange.subscription.infrastructure.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Champ invalide d'une requête")
public record InvalidField(
		@Schema(example = "phoneNumberClient") String field,
		@Schema(example = "doit comporter dix chiffres et commencer par 01, 05 ou 07") String message) {
}
