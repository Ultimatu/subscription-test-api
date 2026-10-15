package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.application.port.in.client.DeletedClient;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DeletedClientResponse(
		Long id,
		String firstName,
		String lastName,
		String email,
		Instant deletedAt,
		String deletedBy,
		@Schema(description = "Date à partir de laquelle le client peut être purgé (délai de conservation)")
		Instant purgeableFrom) {

	static DeletedClientResponse from(DeletedClient client) {
		return new DeletedClientResponse(client.id(), client.firstName(), client.lastName(), client.email(),
				client.deletedAt(), client.deletedBy(), client.purgeableFrom());
	}

}
