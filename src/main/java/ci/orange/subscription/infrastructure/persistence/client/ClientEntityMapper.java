package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;

final class ClientEntityMapper {

	private ClientEntityMapper() {
	}

	static Client toDomain(ClientEntity entity) {
		return Client.restore(entity.getId(), entity.getFirstName(), entity.getLastName(), entity.getEmail(),
				entity.getPhoneNumberClient(), entity.getCity(), entity.getStatus(), entity.toAuditInfo());
	}

	/**
	 * Copie l'état du domaine sur une entité nouvelle ou déjà gérée ; l'identifiant, la version et
	 * l'audit restent gérés par JPA.
	 */
	static void copy(Client client, ClientEntity entity) {
		entity.setFirstName(client.getFirstName());
		entity.setLastName(client.getLastName());
		entity.setEmail(client.getEmail());
		entity.setPhoneNumberClient(client.getPhoneNumberClient());
		entity.setCity(client.getCity());
		entity.setStatus(client.getStatus());
	}

}
