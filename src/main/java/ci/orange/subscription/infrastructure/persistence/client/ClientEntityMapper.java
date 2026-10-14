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

	/** Copies the domain state onto a new or managed entity; id, version and audit stay under JPA control. */
	static void copy(Client client, ClientEntity entity) {
		entity.setFirstName(client.getFirstName());
		entity.setLastName(client.getLastName());
		entity.setEmail(client.getEmail());
		entity.setPhoneNumberClient(client.getPhoneNumberClient());
		entity.setCity(client.getCity());
		entity.setStatus(client.getStatus());
	}

}
