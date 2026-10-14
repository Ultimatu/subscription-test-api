package ci.orange.subscription.infrastructure.security;

import ci.orange.subscription.infrastructure.persistence.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Compte d'un utilisateur de l'API. L'authentification est une préoccupation technique : ce compte
 * vit dans l'infrastructure, le domaine n'en sait rien.
 */
@Getter
@Setter
@Entity
@Table(name = "app_user")
public class UserAccountEntity extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@Column(nullable = false, length = 50)
	private String username;

	/** Empreinte du mot de passe, préfixée par son algorithme ({bcrypt}…), jamais le mot de passe. */
	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Column(nullable = false)
	private boolean enabled;

}
