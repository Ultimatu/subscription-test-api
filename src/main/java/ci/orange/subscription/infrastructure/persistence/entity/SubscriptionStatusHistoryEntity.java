package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Table en ajout seul : les lignes sont insérées, jamais modifiées.
 */
@Getter
@Setter
@Entity
@Table(name = "subscription_status_history")
@EntityListeners(AuditingEntityListener.class)
public class SubscriptionStatusHistoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subscription_id", nullable = false, updatable = false)
	private SubscriptionEntity subscription;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private SubscriptionStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private SubscriptionStatus toStatus;

	@Column(updatable = false, length = 500)
	private String reason;

	@CreatedDate
	@Setter(AccessLevel.NONE)
	@Column(nullable = false, updatable = false)
	private Instant changedAt;

	@CreatedBy
	@Setter(AccessLevel.NONE)
	@Column(nullable = false, updatable = false, length = 100)
	private String changedBy;

}
