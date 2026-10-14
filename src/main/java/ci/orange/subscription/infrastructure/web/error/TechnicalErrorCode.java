package ci.orange.subscription.infrastructure.web.error;

/**
 * Codes des erreurs levées hors du domaine (requête mal formée, accès concurrent, panne
 * inattendue). Les erreurs métier utilisent les codes du domaine ; ensemble, ils forment le champ
 * {@code code} de chaque corps d'erreur.
 */
public enum TechnicalErrorCode {

	AUTHENTICATION_REQUIRED,
	INVALID_TOKEN,
	INVALID_CREDENTIALS,
	ACCESS_DENIED,
	VALIDATION_ERROR,
	MALFORMED_REQUEST,
	INVALID_FIELD_VALUE,
	INVALID_PARAMETER,
	INVALID_SORT_PROPERTY,
	CONCURRENT_MODIFICATION,
	DATA_INTEGRITY_VIOLATION,
	INTERNAL_ERROR

}
