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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Garde des relations JPA vers le client et l'offre (contrairement au domaine, qui ne connaît que
 * leurs identifiants) pour que les requêtes de recherche et de statistiques puissent faire des
 * jointures.
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

	/** Copie de la technologie de l'offre, qui ne change jamais : permet à la base de garantir la règle 14. */
	@Column(nullable = false, updatable = false, length = 20)
	private String technology;

	/** Prix de l'offre au moment de la souscription (ADR-0018). */
	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal monthlyPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status;

	@Column(nullable = false)
	private LocalDate startDate;

	private LocalDate endDate;

}
