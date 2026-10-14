package ci.orange.subscription.infrastructure.web.auth;

import ci.orange.subscription.infrastructure.security.TokenService;
import ci.orange.subscription.infrastructure.security.TokenService.IssuedToken;
import ci.orange.subscription.infrastructure.web.common.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seul endpoint public de l'API avec la documentation et le point de santé : il échange un
 * identifiant et un mot de passe contre un jeton d'accès.
 */
@Tag(name = "Authentification", description = "Obtention d'un jeton d'accès")
@RestController
@RequestMapping("/auth")
class AuthController {

	private final AuthenticationManager authenticationManager;
	private final TokenService tokenService;

	AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
		this.authenticationManager = authenticationManager;
		this.tokenService = tokenService;
	}

	@Operation(summary = "Se connecter",
			description = "Renvoie un jeton d'accès (JWT) à transmettre dans l'en-tête Authorization: Bearer. "
					+ "Comptes de démonstration : admin / Admin@2026 et conseiller / Conseiller@2026.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Jeton émis")
	@ApiErrors.BadRequest
	@ApiErrors.Unauthorized
	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		Authentication authentication = authenticationManager
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
		IssuedToken token = tokenService.issue(authentication);
		return new TokenResponse(token.value(), "Bearer", token.expiresIn(), token.roles());
	}

}
