package ci.orange.subscription.infrastructure.security;

import ci.orange.subscription.infrastructure.web.error.ProblemDetailFactory;
import ci.orange.subscription.infrastructure.web.error.TechnicalErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Réponses 401 et 403 des filtres de sécurité, au même format Problem Details que les autres
 * erreurs : elles surviennent avant d'atteindre un controller, hors de portée du gestionnaire
 * d'exceptions global.
 */
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ProblemDetailFactory problems;
	private final ObjectMapper objectMapper;

	ProblemDetailsSecurityHandler(ProblemDetailFactory problems, ObjectMapper objectMapper) {
		this.problems = problems;
		this.objectMapper = objectMapper;
	}

	/** Aucun jeton, ou un jeton invalide ou expiré. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
			throws IOException {
		boolean invalidToken = exception instanceof InvalidBearerTokenException;
		ProblemDetail problem = invalidToken
				? problems.create(HttpStatus.UNAUTHORIZED, "Authentification requise", "Jeton invalide ou expiré",
						TechnicalErrorCode.INVALID_TOKEN.name())
				: problems.create(HttpStatus.UNAUTHORIZED, "Authentification requise",
						"Un jeton d'accès est requis (en-tête Authorization: Bearer), obtenu par POST /api/auth/login",
						TechnicalErrorCode.AUTHENTICATION_REQUIRED.name());
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
		write(request, response, problem);
	}

	/** Jeton valide, mais rôle insuffisant. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		write(request, response, problems.create(HttpStatus.FORBIDDEN, "Accès refusé",
				"Votre rôle ne permet pas cette opération", TechnicalErrorCode.ACCESS_DENIED.name()));
	}

	private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
			throws IOException {
		problem.setInstance(URI.create(request.getRequestURI()));
		response.setStatus(problem.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

}
