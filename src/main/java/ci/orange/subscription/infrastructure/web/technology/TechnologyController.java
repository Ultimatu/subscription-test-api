package ci.orange.subscription.infrastructure.web.technology;

import ci.orange.subscription.application.port.in.technology.TechnologyUseCases;
import ci.orange.subscription.domain.technology.Technology;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Catalogue of access technologies. Not part of the specification: it lets the business add a
 * technology without any code change.
 */
@RestController
@RequestMapping("/technologies")
@RequiredArgsConstructor
class TechnologyController {

	private final TechnologyUseCases technologyUseCases;

	@GetMapping
	List<TechnologyResponse> list() {
		return technologyUseCases.list().stream().map(TechnologyResponse::from).toList();
	}

	@GetMapping("/{code}")
	TechnologyResponse get(@PathVariable String code) {
		return TechnologyResponse.from(technologyUseCases.get(code));
	}

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
