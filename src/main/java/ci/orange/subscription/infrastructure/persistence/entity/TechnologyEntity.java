package ci.orange.subscription.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The code is a natural key, assigned by the caller: the null version tells JPA the entity is new.
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

}
