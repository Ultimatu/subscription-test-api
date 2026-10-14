package ci.orange.subscription.infrastructure.web.subscription;

import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import ci.orange.subscription.infrastructure.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscriptions seen from their client: the URL starts with /clients but the resource is the
 * subscription.
 */
@Tag(name = "Souscriptions")
@RestController
@RequestMapping("/clients/{clientId}/subscriptions")
class ClientSubscriptionController {

	private final SubscriptionUseCases subscriptionUseCases;

	ClientSubscriptionController(SubscriptionUseCases subscriptionUseCases) {
		this.subscriptionUseCases = subscriptionUseCases;
	}

	@Operation(summary = "Souscriptions d'un client")
	@ApiResponse(responseCode = "200", description = "Page de souscriptions du client")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@GetMapping
	PageResponse<SubscriptionResponse> list(@PathVariable Long clientId,
			@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return PageResponse.from(subscriptionUseCases.listByClient(clientId, pageable), SubscriptionResponse::from);
	}

}
