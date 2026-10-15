package ci.orange.subscription.infrastructure.web.technology;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.technology.Technology;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record TechnologyResponse(String code, String label,
		@Schema(description = "Disponible pour de nouvelles offres et souscriptions") boolean active, Instant createdAt,
		String createdBy) {

	static TechnologyResponse from(Technology technology) {
		AuditInfo audit = technology.getAudit() != null ? technology.getAudit() : new AuditInfo(null, null, null, null);
		return new TechnologyResponse(technology.getCode(), technology.getLabel(), technology.isActive(),
				audit.createdAt(), audit.createdBy());
	}

}
