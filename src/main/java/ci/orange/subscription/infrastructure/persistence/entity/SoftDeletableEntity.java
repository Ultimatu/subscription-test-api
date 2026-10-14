package ci.orange.subscription.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;

/**
 * Les lignes supprimées sont conservées pour la traçabilité et masquées dans toutes les requêtes
 * par le {@code @SQLRestriction("deleted_at is null")} de chaque sous-classe.
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
