package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.domain.offer.Technology;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "offer")
public class OfferEntity extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@Column(nullable = false, updatable = false, length = 50)
	private String code;

	@Column(nullable = false)
	private String label;

	@Convert(converter = TechnologyConverter.class)
	@Column(nullable = false, updatable = false, length = 20)
	private Technology technology;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal monthlyPrice;

	@Column(nullable = false)
	private boolean active;

}
