package ci.orange.subscription.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;

/**
 * Deleted rows are kept for traceability and hidden from every query by the
 * {@code @SQLRestriction("deleted_at is null")} of each subclass.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends AuditableEntity {

	private Instant deletedAt;

	@Column(length = 100)
	private String deletedBy;

	public void markDeleted(Instant deletedAt, String deletedBy) {
		this.deletedAt = deletedAt;
		this.deletedBy = deletedBy;
	}

}
