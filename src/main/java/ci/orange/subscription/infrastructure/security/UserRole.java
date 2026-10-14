package ci.orange.subscription.infrastructure.security;

/**
 * Rôles des utilisateurs de l'API (ADR-0013). Le conseiller gère la relation client au quotidien ;
 * l'administrateur gère en plus le catalogue, les suppressions et les statistiques.
 */
public enum UserRole {

	ADMIN,
	CONSEILLER

}
