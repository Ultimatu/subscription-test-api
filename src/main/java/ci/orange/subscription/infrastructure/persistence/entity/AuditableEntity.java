package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.domain.common.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

	@Version
	@Column(nullable = false)
	private Long version;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@CreatedBy
	@Column(nullable = false, updatable = false, length = 100)
	private String createdBy;

	@LastModifiedBy
	@Column(nullable = false, length = 100)
	private String updatedBy;

	public AuditInfo toAuditInfo() {
		return new AuditInfo(createdAt, createdBy, updatedAt, updatedBy);
	}

}
