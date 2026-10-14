package ci.orange.subscription.infrastructure.web.error;

import ci.orange.subscription.infrastructure.web.tracing.RequestTracingFilter;
import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Fabrique unique des corps d'erreur, pour que toutes les erreurs aient le même format : celles des
 * controllers ({@link GlobalExceptionHandler}) comme celles des filtres de sécurité (401, 403), qui
 * surviennent avant d'atteindre un controller.
 */
@Component
public class ProblemDetailFactory {

	private final Clock clock;

	public ProblemDetailFactory(Clock clock) {
		this.clock = clock;
	}

	public ProblemDetail create(HttpStatus status, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		enrich(problem, code);
		return problem;
	}

	/** Ajoute le code, l'horodatage et l'identifiant de la requête. */
	public void enrich(ProblemDetail problem, String code) {
		problem.setProperty("code", code);
		problem.setProperty("timestamp", clock.instant());
		String requestId = MDC.get(RequestTracingFilter.REQUEST_ID_MDC_KEY);
		if (requestId != null) {
			problem.setProperty("requestId", requestId);
		}
	}

}
