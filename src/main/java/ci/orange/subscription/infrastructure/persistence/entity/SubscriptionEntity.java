package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Keeps JPA relations to the client and the offer (unlike the domain, which only holds their ids)
 * so that search and statistics queries can join on them.
 */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "subscription")
public class SubscriptionEntity extends SoftDeletableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String reference;

	@Column(nullable = false, updatable = false, length = 10)
	private String nd;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "client_id", nullable = false, updatable = false)
	private ClientEntity client;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "offer_id", nullable = false, updatable = false)
	private OfferEntity offer;

	/** Copy of the offer's technology, which never changes: lets the database guarantee rule 14. */
	@Column(nullable = false, updatable = false, length = 20)
	private String technology;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status;

	@Column(nullable = false)
	private LocalDate startDate;

	private LocalDate endDate;

}
