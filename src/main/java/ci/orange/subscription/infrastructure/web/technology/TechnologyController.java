package ci.orange.subscription.infrastructure.web.technology;

import ci.orange.subscription.application.port.in.technology.TechnologyUseCases;
import ci.orange.subscription.domain.technology.Technology;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Catalogue des technologies d'accès. Hors énoncé : il permet au métier d'ajouter une technologie
 * sans modifier le code.
 */
@Tag(name = "Technologies", description = "Catalogue des technologies d'accès (hors énoncé)")
@RestController
@RequestMapping("/technologies")
class TechnologyController {

	private final TechnologyUseCases technologyUseCases;

	TechnologyController(TechnologyUseCases technologyUseCases) {
		this.technologyUseCases = technologyUseCases;
	}

	@Operation(summary = "Lister les technologies", description = "Catalogue complet, trié par code.")
	@ApiResponse(responseCode = "200", description = "Catalogue des technologies")
	@GetMapping
	List<TechnologyResponse> list() {
		return technologyUseCases.list().stream().map(TechnologyResponse::from).toList();
	}

	@Operation(summary = "Détail d'une technologie")
	@ApiResponse(responseCode = "200", description = "Technologie trouvée")
	@ApiErrors.NotFound
	@GetMapping("/{code}")
	TechnologyResponse get(@PathVariable String code) {
		return TechnologyResponse.from(technologyUseCases.get(code));
	}

	@Operation(summary = "Ajouter une technologie",
			description = "Permet de lancer une nouvelle technologie sans modification du code.")
	@ApiResponse(responseCode = "201", description = "Technologie créée")
	@ApiErrors.BadRequest
	@ApiErrors.Conflict
	@PostMapping
	ResponseEntity<TechnologyResponse> create(@Valid @RequestBody CreateTechnologyRequest request) {
		Technology technology = technologyUseCases.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{code}")
				.buildAndExpand(technology.getCode())
				.toUri();
		return ResponseEntity.created(location).body(TechnologyResponse.from(technology));
	}

}
