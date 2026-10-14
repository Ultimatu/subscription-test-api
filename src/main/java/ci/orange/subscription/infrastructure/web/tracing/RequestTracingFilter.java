package ci.orange.subscription.infrastructure.web.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Donne un identifiant à chaque requête : le X-Request-Id de l'appelant s'il est valide, un nouveau
 * sinon. Il est renvoyé dans l'en-tête de la réponse, ajouté à chaque ligne de log de la requête
 * (MDC) et aux corps d'erreur, pour qu'une erreur signalée par un utilisateur se retrouve dans les
 * logs. Écrit aussi une ligne de log par appel à l'API (méthode, chemin, statut, durée). La query
 * string est volontairement omise : les paramètres de recherche peuvent contenir des données
 * personnelles.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	public static final String REQUEST_ID_MDC_KEY = "requestId";

	/**
	 * Une valeur fournie par l'appelant n'est acceptée que sous cette forme, pour qu'elle ne puisse
	 * pas forger de lignes de log.
	 */
	private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

	private static final String LOGGED_PATH_PREFIX = "/api/";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
		long start = System.nanoTime();
		MDC.put(REQUEST_ID_MDC_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			if (request.getRequestURI().startsWith(LOGGED_PATH_PREFIX)) {
				log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(),
						TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
			}
			MDC.remove(REQUEST_ID_MDC_KEY);
		}
	}

	private static String resolveRequestId(String candidate) {
		return candidate != null && VALID_REQUEST_ID.matcher(candidate).matches() ? candidate
				: UUID.randomUUID().toString();
	}

}
