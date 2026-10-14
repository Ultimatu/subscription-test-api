package ci.orange.subscription.infrastructure.web.technology;

import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.technology.Technology;
import java.time.Instant;

public record TechnologyResponse(String code, String label, Instant createdAt, String createdBy) {

	static TechnologyResponse from(Technology technology) {
		AuditInfo audit = technology.getAudit() != null ? technology.getAudit() : new AuditInfo(null, null, null, null);
		return new TechnologyResponse(technology.getCode(), technology.getLabel(), audit.createdAt(),
				audit.createdBy());
	}

}
