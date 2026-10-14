package ci.orange.subscription.infrastructure.web.error;

import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainException;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.InvalidStatusTransitionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Transforme chaque erreur en Problem Details (RFC 9457), avec un code stable, un horodatage et
 * l'identifiant de la requête. Les exceptions du domaine sont associées ici à leur statut HTTP : le
 * domaine ne connaît jamais HTTP.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String INVALID_REQUEST = "Requête invalide";

	private final ProblemDetailFactory problems;

	public GlobalExceptionHandler(ProblemDetailFactory problems) {
		this.problems = problems;
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Ressource introuvable", exception);
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	ProblemDetail handleInvalidTransition(InvalidStatusTransitionException exception) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Transition de statut interdite", exception);
		problem.setProperty("currentStatus", exception.getCurrentStatus());
		problem.setProperty("requiredStatuses", exception.getRequiredStatuses());
		return problem;
	}

	@ExceptionHandler(BusinessRuleViolationException.class)
	ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException exception) {
		return problem(HttpStatus.CONFLICT, "Règle métier non respectée", exception);
	}

	@ExceptionHandler(DomainValidationException.class)
	ProblemDetail handleDomainValidation(DomainValidationException exception) {
		return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, exception);
	}

	/** Échec de connexion : même réponse pour un compte inconnu ou un mauvais mot de passe. */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleFailedAuthentication(AuthenticationException exception) {
		return problem(HttpStatus.UNAUTHORIZED, "Authentification échouée", "Identifiant ou mot de passe incorrect",
				TechnicalErrorCode.INVALID_CREDENTIALS.name());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return problem(HttpStatus.FORBIDDEN, "Accès refusé", "Votre rôle ne permet pas cette opération",
				TechnicalErrorCode.ACCESS_DENIED.name());
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
		return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
				"Tri impossible sur la propriété inconnue : " + exception.getPropertyName(),
				TechnicalErrorCode.INVALID_SORT_PROPERTY.name());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception) {
		return problem(HttpStatus.CONFLICT, "Modification concurrente",
				"La ressource a été modifiée entre-temps, veuillez réessayer",
				TechnicalErrorCode.CONCURRENT_MODIFICATION.name());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		log.warn("Data integrity violation not translated into a business error", exception);
		return problem(HttpStatus.CONFLICT, "Conflit de données",
				"L'opération viole une contrainte d'intégrité des données",
				TechnicalErrorCode.DATA_INTEGRITY_VIOLATION.name());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception) {
		log.error("Unexpected error", exception);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne", "Une erreur inattendue est survenue",
				TechnicalErrorCode.INTERNAL_ERROR.name());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Un ou plusieurs champs sont invalides",
				TechnicalErrorCode.VALIDATION_ERROR.name());
		problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
				.toList());
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
				"Le corps de la requête est illisible ou mal formé", TechnicalErrorCode.MALFORMED_REQUEST.name());
		if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
			problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
					mapping instanceof InvalidFormatException invalid
							? "Valeur invalide pour le champ %s : %s".formatted(fieldPath(mapping), invalid.getValue())
							: "Valeur invalide pour le champ " + fieldPath(mapping),
					TechnicalErrorCode.INVALID_FIELD_VALUE.name());
		}
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = exception instanceof MethodArgumentTypeMismatchException argument ? argument.getName()
				: exception.getPropertyName();
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
				"Valeur invalide pour le paramètre %s : %s".formatted(name, exception.getValue()),
				TechnicalErrorCode.INVALID_PARAMETER.name());
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	/**
	 * Toute erreur gérée par Spring lui-même (405, 415, route inconnue…) passe par ici : elle
	 * reçoit le même enrichissement, avec un code tiré de son statut HTTP (METHOD_NOT_ALLOWED…).
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			problems.enrich(problem, status != null ? status.name() : "HTTP_" + statusCode.value());
		}
		return response;
	}

	private ProblemDetail problem(HttpStatus status, String title, DomainException exception) {
		return problem(status, title, exception.getMessage(), exception.getErrorCode().name());
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		return problems.create(status, title, detail, code);
	}

	private static String fieldPath(JsonMappingException exception) {
		return exception.getPath().stream()
				.map(reference -> reference.getFieldName() != null ? reference.getFieldName()
						: "[" + reference.getIndex() + "]")
				.collect(Collectors.joining("."));
	}

}
