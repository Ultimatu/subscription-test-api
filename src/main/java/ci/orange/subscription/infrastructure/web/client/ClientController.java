package ci.orange.subscription.infrastructure.web.client;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import ci.orange.subscription.infrastructure.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
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
 * Uniquement du HTTP : valide l'entrée, appelle le cas d'usage et convertit le résultat. Aucune
 * règle métier ici.
 */
@Tag(name = "Clients", description = "Gestion des clients")
@ApiErrors.Unauthorized
@ApiErrors.Forbidden
@RestController
@RequestMapping("/clients")
class ClientController {

	private final ClientUseCases clientUseCases;

	ClientController(ClientUseCases clientUseCases) {
		this.clientUseCases = clientUseCases;
	}

	@Operation(summary = "Créer un client",
			description = "Le client est créé au statut ACTIVE : un statut fourni dans le corps est ignoré. "
					+ "L'email est unique (insensible à la casse).")
	@ApiResponse(responseCode = "201", description = "Client créé")
	@ApiErrors.BadRequest
	@ApiErrors.Conflict
	@PostMapping
	ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
		Client client = clientUseCases.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(client.getId())
				.toUri();
		return ResponseEntity.created(location).body(ClientResponse.from(client));
	}

	@Operation(summary = "Rechercher des clients", description = "Filtres optionnels, combinés entre eux.")
	@ApiResponse(responseCode = "200", description = "Page de clients")
	@ApiErrors.BadRequest
	@GetMapping
	PageResponse<ClientResponse> search(
			@Parameter(description = "Ville exacte, insensible à la casse") @RequestParam(required = false) String city,
			@RequestParam(required = false) ClientStatus status,
			@Parameter(description = "Recherche partielle, insensible à la casse, sur le nom, le prénom et l'email")
			@RequestParam(required = false) String search,
			@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return PageResponse.from(clientUseCases.search(new ClientSearchCriteria(city, status, search), pageable),
				ClientResponse::from);
	}

	@Operation(summary = "Détail d'un client")
	@ApiResponse(responseCode = "200", description = "Client trouvé")
	@ApiErrors.NotFound
	@GetMapping("/{id}")
	ClientResponse get(@PathVariable Long id) {
		return ClientResponse.from(clientUseCases.get(id));
	}

	@Operation(summary = "Modifier un client",
			description = "Le statut est modifiable dans les deux sens. L'email n'est pas modifiable : "
					+ "le fournir renvoie une erreur 400.")
	@ApiResponse(responseCode = "200", description = "Client modifié")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PutMapping("/{id}")
	ClientResponse update(@PathVariable Long id, @Valid @RequestBody UpdateClientRequest request) {
		return ClientResponse.from(clientUseCases.update(id, request.toCommand()));
	}

	@Operation(summary = "Supprimer un client",
			description = "Refusé (409) si le client a une souscription PENDING, ACTIVE ou SUSPENDED. "
					+ "Le client et ses souscriptions résiliées sont conservés en base (suppression logique).")
	@ApiResponse(responseCode = "204", description = "Client supprimé")
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable Long id) {
		clientUseCases.delete(id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Synthèse d'un client",
			description = "Nombre de souscriptions par statut et revenu mensuel total des souscriptions ACTIVE.")
	@ApiResponse(responseCode = "200", description = "Synthèse du client")
	@ApiErrors.NotFound
	@GetMapping("/{id}/summary")
	ClientSummaryResponse summary(@PathVariable Long id) {
		return ClientSummaryResponse.from(clientUseCases.summarize(id));
	}

}
