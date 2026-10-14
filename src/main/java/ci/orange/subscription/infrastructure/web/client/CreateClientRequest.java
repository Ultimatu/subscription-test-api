package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.domain.client.Client;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Un « status » envoyé dans le corps est simplement ignoré : un nouveau client est toujours ACTIVE
 * (règle 3).
 */
public record CreateClientRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Pattern(regexp = Client.PHONE_NUMBER_REGEX,
				message = "doit comporter dix chiffres et commencer par 01, 05 ou 07") String phoneNumberClient,
		@NotBlank @Size(max = 100) String city) {

	CreateClientCommand toCommand() {
		return new CreateClientCommand(firstName, lastName, email, phoneNumberClient, city);
	}

}
