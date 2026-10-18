package ci.orange.subscription.application.port.in.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Journal d'audit des actions métier (ADR-0013).
 */
public interface AuditUseCases {

	/** Du plus récent au plus ancien. */
	Page<AuditEvent> search(AuditSearchCriteria criteria, Pageable pageable);

}
