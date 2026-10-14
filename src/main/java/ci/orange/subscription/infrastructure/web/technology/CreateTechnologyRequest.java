package ci.orange.subscription.infrastructure.web.technology;

import ci.orange.subscription.application.port.in.technology.CreateTechnologyCommand;
import ci.orange.subscription.domain.technology.Technology;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTechnologyRequest(
		@NotBlank @Size(max = Technology.CODE_MAX_LENGTH) @Pattern(regexp = Technology.CODE_REGEX,
				message = "doit être en majuscules (lettres, chiffres et _), ex. 4G_BOX") String code,
		@NotBlank @Size(max = 100) String label) {

	CreateTechnologyCommand toCommand() {
		return new CreateTechnologyCommand(code, label);
	}

}
