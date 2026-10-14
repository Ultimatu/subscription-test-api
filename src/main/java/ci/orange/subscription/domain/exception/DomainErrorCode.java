package ci.orange.subscription.domain.exception;

/**
 * Stable, machine-readable identifier of every business error, exposed as is to API clients so
 * they can react to a specific case without parsing messages. Never rename a constant: add one.
 */
public enum DomainErrorCode {

	// Invalid data (invariants of the domain)
	REQUIRED_FIELD,
	INVALID_PHONE_NUMBER,
	INVALID_OFFER_CODE,
	INVALID_MONTHLY_PRICE,
	INVALID_TECHNOLOGY_CODE,
	UNKNOWN_TECHNOLOGY,
	START_DATE_IN_PAST,

	// Unknown resources
	CLIENT_NOT_FOUND,
	OFFER_NOT_FOUND,
	SUBSCRIPTION_NOT_FOUND,
	TECHNOLOGY_NOT_FOUND,

	// Business rules
	CLIENT_EMAIL_ALREADY_USED,
	CLIENT_HAS_ONGOING_SUBSCRIPTIONS,
	OFFER_CODE_ALREADY_USED,
	TECHNOLOGY_ALREADY_EXISTS,
	CLIENT_INACTIVE,
	OFFER_INACTIVE,
	CLIENT_HAS_SUSPENDED_SUBSCRIPTION,
	TECHNOLOGY_ALREADY_SUBSCRIBED,
	INVALID_STATUS_TRANSITION,
	SUBSCRIPTION_NOT_DELETABLE

}
