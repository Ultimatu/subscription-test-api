package ci.orange.subscription.infrastructure.web.error;

/**
 * Codes of the errors raised outside of the domain (malformed request, concurrency, unexpected
 * failure). Business errors use the domain's codes; together they form the {@code code} field of
 * every error body.
 */
public enum TechnicalErrorCode {

	VALIDATION_ERROR,
	MALFORMED_REQUEST,
	INVALID_FIELD_VALUE,
	INVALID_PARAMETER,
	INVALID_SORT_PROPERTY,
	CONCURRENT_MODIFICATION,
	DATA_INTEGRITY_VIOLATION,
	INTERNAL_ERROR

}
