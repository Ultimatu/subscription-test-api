package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.infrastructure.web.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP only: validates the input, calls the use case and maps the result. No business rule here.
 */
@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
class ClientController {

	private final ClientUseCases clientUseCases;

	@PostMapping
	ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
		Client client = clientUseCases.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(client.getId())
				.toUri();
		return ResponseEntity.created(location).body(ClientResponse.from(client));
	}

	@GetMapping
	PageResponse<ClientResponse> search(@RequestParam(required = false) String city,
			@RequestParam(required = false) ClientStatus status, @RequestParam(required = false) String search,
			@PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return PageResponse.from(clientUseCases.search(new ClientSearchCriteria(city, status, search), pageable),
				ClientResponse::from);
	}

	@GetMapping("/{id}")
	ClientResponse get(@PathVariable Long id) {
		return ClientResponse.from(clientUseCases.get(id));
	}

	@PutMapping("/{id}")
	ClientResponse update(@PathVariable Long id, @Valid @RequestBody UpdateClientRequest request) {
		return ClientResponse.from(clientUseCases.update(id, request.toCommand()));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		clientUseCases.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/summary")
	ClientSummaryResponse summary(@PathVariable Long id) {
		return ClientSummaryResponse.from(clientUseCases.summarize(id));
	}

}
