package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.application.port.in.client.UpdateClientCommand;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code email} is only declared to reject any attempt to change it (rule 1): it must be absent.
 */
public record UpdateClientRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Pattern(regexp = Client.PHONE_NUMBER_REGEX,
				message = "doit comporter dix chiffres et commencer par 01, 05 ou 07") String phoneNumberClient,
		@NotBlank @Size(max = 100) String city,
		@NotNull ClientStatus status,
		@Null(message = "n'est pas modifiable après la création du client") String email) {

	UpdateClientCommand toCommand() {
		return new UpdateClientCommand(firstName, lastName, phoneNumberClient, city, status);
	}

}
