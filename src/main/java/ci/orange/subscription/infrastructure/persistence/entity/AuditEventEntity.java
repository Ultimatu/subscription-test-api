package ci.orange.subscription.infrastructure.persistence.entity;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un événement du journal d'audit (ADR-0013). Table en ajout seul : aucune colonne n'est modifiable.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(nullable = false, updatable = false, length = 100)
	private String actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private AuditResource resourceType;

	@Column(updatable = false, length = 50)
	private String resourceId;

	@Column(updatable = false)
	private Long clientId;

	@Column(nullable = false, updatable = false)
	private String summary;

	/** Colonne jsonb, lue et écrite par Jackson. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private Map<String, Object> details;

	@Column(updatable = false, length = 64)
	private String requestId;

}
