package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.client.ClientStatus;

/**
 * No email: it cannot change after creation (rule 1).
 */
public record UpdateClientCommand(String firstName, String lastName, String phoneNumberClient, String city,
		ClientStatus status) {
}
