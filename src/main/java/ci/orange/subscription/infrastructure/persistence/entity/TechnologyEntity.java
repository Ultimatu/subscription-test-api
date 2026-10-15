package ci.orange.subscription.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Le code est une clé naturelle, fournie par l'appelant : la version nulle indique à JPA que
 * l'entité est nouvelle.
 */
@Getter
@Setter
@Entity
@Table(name = "technology")
public class TechnologyEntity extends AuditableEntity {

	@Id
	@Column(length = 20)
	private String code;

	@Column(nullable = false, length = 100)
	private String label;

	@Column(nullable = false)
	private boolean active;

}
