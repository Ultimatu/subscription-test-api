package ci.orange.subscription.infrastructure.web.error;

import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 problem detail: domain exceptions are mapped to their HTTP
 * status here, so the domain never deals with HTTP.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String INVALID_REQUEST = "Requête invalide";

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleNotFound(ResourceNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Ressource introuvable", exception.getMessage());
	}

	@ExceptionHandler(InvalidStatusTransitionException.class)
	ProblemDetail handleInvalidTransition(InvalidStatusTransitionException exception) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "Transition de statut interdite", exception.getMessage());
		problem.setProperty("currentStatus", exception.getCurrentStatus());
		problem.setProperty("requiredStatuses", exception.getRequiredStatuses());
		return problem;
	}

	@ExceptionHandler(BusinessRuleViolationException.class)
	ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException exception) {
		return problem(HttpStatus.CONFLICT, "Règle métier non respectée", exception.getMessage());
	}

	@ExceptionHandler(DomainValidationException.class)
	ProblemDetail handleDomainValidation(DomainValidationException exception) {
		return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException exception) {
		return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
				"Tri impossible sur la propriété inconnue : " + exception.getPropertyName());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception) {
		return problem(HttpStatus.CONFLICT, "Modification concurrente",
				"La ressource a été modifiée entre-temps, veuillez réessayer");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		log.warn("Data integrity violation not translated into a business error", exception);
		return problem(HttpStatus.CONFLICT, "Conflit de données",
				"L'opération viole une contrainte d'intégrité des données");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception) {
		log.error("Unexpected error", exception);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne", "Une erreur inattendue est survenue");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Un ou plusieurs champs sont invalides");
		problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
				.toList());
		return handleExceptionInternal(exception, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = "Le corps de la requête est illisible ou mal formé";
		if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
			detail = mapping instanceof InvalidFormatException invalid
					? "Valeur invalide pour le champ %s : %s".formatted(fieldPath(mapping), invalid.getValue())
					: "Valeur invalide pour le champ " + fieldPath(mapping);
		}
		return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, detail), headers,
				status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = exception instanceof MethodArgumentTypeMismatchException argument ? argument.getName()
				: exception.getPropertyName();
		String detail = "Valeur invalide pour le paramètre %s : %s".formatted(name, exception.getValue());
		return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, detail), headers,
				status, request);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

	private static String fieldPath(JsonMappingException exception) {
		return exception.getPath().stream()
				.map(reference -> reference.getFieldName() != null ? reference.getFieldName()
						: "[" + reference.getIndex() + "]")
				.collect(Collectors.joining("."));
	}

	record InvalidField(String field, String message) {
	}

}
