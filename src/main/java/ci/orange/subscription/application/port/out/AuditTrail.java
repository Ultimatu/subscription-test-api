package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditEvent;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Journal d'audit (ADR-0013). L'auteur, l'heure et l'identifiant de la requête sont renseignés par
 * l'adaptateur.
 */
public interface AuditTrail {

	/**
	 * Dans la transaction de l'action quand elle existe : l'événement n'est consigné que si l'action
	 * est validée.
	 */
	void record(Entry entry);

	Page<AuditEvent> search(AuditSearchCriteria criteria, Instant from, Instant to, Pageable pageable);

	/** Efface les événements d'un client, qui contiennent ses données personnelles (purge). */
	void eraseClient(Long clientId);

	/**
	 * @param clientId client concerné, pour effacer l'événement avec lui ; nul si aucun
	 * @param actor auteur imposé (« system » pour une tâche planifiée) ; nul : l'utilisateur connecté
	 */
	record Entry(AuditAction action, AuditResource resourceType, String resourceId, Long clientId, String summary,
			Map<String, Object> details, String actor) {

		public Entry(AuditAction action, AuditResource resourceType, Object resourceId, Long clientId, String summary,
				Map<String, Object> details) {
			this(action, resourceType, resourceId == null ? null : resourceId.toString(), clientId, summary, details,
					null);
		}

	}

}
