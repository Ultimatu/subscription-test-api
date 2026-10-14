package ci.orange.subscription.infrastructure.web.offer;

import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.in.offer.OfferUseCases;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import ci.orange.subscription.infrastructure.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Offres", description = "Catalogue des offres")
@RestController
@RequestMapping("/offers")
@RequiredArgsConstructor
class OfferController {

	private final OfferUseCases offerUseCases;

	@Operation(summary = "Créer une offre",
			description = "L'offre est créée active : un champ active fourni est ignoré. Le code est unique et en "
					+ "majuscules ; la technologie doit exister dans le catalogue.")
	@ApiResponse(responseCode = "201", description = "Offre créée")
	@ApiErrors.BadRequest
	@ApiErrors.Conflict
	@PostMapping
	ResponseEntity<OfferResponse> create(@Valid @RequestBody CreateOfferRequest request) {
		Offer offer = offerUseCases.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(offer.getId())
				.toUri();
		return ResponseEntity.created(location).body(OfferResponse.from(offer));
	}

	@Operation(summary = "Lister les offres", description = "Filtres optionnels, combinés entre eux.")
	@ApiResponse(responseCode = "200", description = "Page d'offres")
	@ApiErrors.BadRequest
	@GetMapping
	PageResponse<OfferResponse> search(
			@Parameter(description = "Code de technologie, ex. FTTH") @RequestParam(required = false) String technology,
			@RequestParam(required = false) Boolean active,
			@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
		return PageResponse.from(offerUseCases.search(new OfferSearchCriteria(technology, active), pageable),
				OfferResponse::from);
	}

	@Operation(summary = "Détail d'une offre")
	@ApiResponse(responseCode = "200", description = "Offre trouvée")
	@ApiErrors.NotFound
	@GetMapping("/{id}")
	OfferResponse get(@PathVariable Long id) {
		return OfferResponse.from(offerUseCases.get(id));
	}

	@Operation(summary = "Modifier une offre",
			description = "Seuls le libellé et le prix sont modifiables. Fournir code, technology ou active "
					+ "renvoie une erreur 400.")
	@ApiResponse(responseCode = "200", description = "Offre modifiée")
	@ApiErrors.BadRequest
	@ApiErrors.NotFound
	@ApiErrors.Conflict
	@PutMapping("/{id}")
	OfferResponse update(@PathVariable Long id, @Valid @RequestBody UpdateOfferRequest request) {
		return OfferResponse.from(offerUseCases.update(id, request.toCommand()));
	}

	@Operation(summary = "Retirer une offre du catalogue",
			description = "Les souscriptions existantes ne sont pas affectées ; toute nouvelle souscription sur "
					+ "cette offre est refusée. Sans effet si l'offre est déjà inactive.")
	@ApiResponse(responseCode = "200", description = "Offre désactivée")
	@ApiErrors.NotFound
	@PatchMapping("/{id}/deactivate")
	OfferResponse deactivate(@PathVariable Long id) {
		return OfferResponse.from(offerUseCases.deactivate(id));
	}

}
