package ci.orange.subscription.application.port.in.client;

/**
 * Pas de statut : un nouveau client est toujours ACTIVE (règle 3).
 */
public record CreateClientCommand(String firstName, String lastName, String email, String phoneNumberClient,
		String city) {
}
