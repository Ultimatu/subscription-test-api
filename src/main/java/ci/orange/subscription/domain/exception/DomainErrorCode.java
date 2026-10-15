package ci.orange.subscription.domain.exception;

/**
 * Identifiant stable, exploitable par programme, de chaque erreur métier. Il est exposé tel quel
 * aux clients de l'API pour qu'ils réagissent à un cas précis sans analyser les messages. Ne jamais
 * renommer une constante : en ajouter une.
 */
public enum DomainErrorCode {

	// Données invalides (invariants du domaine)
	REQUIRED_FIELD,
	INVALID_PHONE_NUMBER,
	INVALID_OFFER_CODE,
	INVALID_MONTHLY_PRICE,
	INVALID_TECHNOLOGY_CODE,
	UNKNOWN_TECHNOLOGY,
	START_DATE_IN_PAST,
	INVALID_DATE_RANGE,

	// Ressources inconnues
	CLIENT_NOT_FOUND,
	OFFER_NOT_FOUND,
	SUBSCRIPTION_NOT_FOUND,
	TECHNOLOGY_NOT_FOUND,

	// Règles métier
	CLIENT_EMAIL_ALREADY_USED,
	CLIENT_HAS_ONGOING_SUBSCRIPTIONS,
	OFFER_CODE_ALREADY_USED,
	TECHNOLOGY_ALREADY_EXISTS,
	CLIENT_INACTIVE,
	OFFER_INACTIVE,
	TECHNOLOGY_INACTIVE,
	CLIENT_HAS_SUSPENDED_SUBSCRIPTION,
	TECHNOLOGY_ALREADY_SUBSCRIBED,
	INVALID_STATUS_TRANSITION,
	SUBSCRIPTION_NOT_DELETABLE,
	RETENTION_PERIOD_NOT_ELAPSED,
	HARD_DELETE_DISABLED

}
