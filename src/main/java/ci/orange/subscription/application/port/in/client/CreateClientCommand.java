package ci.orange.subscription.application.port.in.client;

/**
 * No status: a new client is always ACTIVE (rule 3).
 */
public record CreateClientCommand(String firstName, String lastName, String email, String phoneNumberClient,
		String city) {
}
