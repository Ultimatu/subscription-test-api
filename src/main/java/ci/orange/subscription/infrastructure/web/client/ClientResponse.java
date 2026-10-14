package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.common.AuditInfo;
import java.time.Instant;

public record ClientResponse(
		Long id,
		String firstName,
		String lastName,
		String email,
		String phoneNumberClient,
		String city,
		ClientStatus status,
		Instant createdAt,
		String createdBy,
		Instant updatedAt,
		String updatedBy) {

	static ClientResponse from(Client client) {
		AuditInfo audit = client.getAudit() != null ? client.getAudit() : new AuditInfo(null, null, null, null);
		return new ClientResponse(client.getId(), client.getFirstName(), client.getLastName(), client.getEmail(),
				client.getPhoneNumberClient(), client.getCity(), client.getStatus(), audit.createdAt(),
				audit.createdBy(), audit.updatedAt(), audit.updatedBy());
	}

}
