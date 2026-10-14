package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.client.ClientStatus;

/**
 * Pas d'email : il n'est plus modifiable après la création (règle 1).
 */
public record UpdateClientCommand(String firstName, String lastName, String phoneNumberClient, String city,
		ClientStatus status) {
}
