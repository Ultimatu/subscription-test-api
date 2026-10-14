package ci.orange.subscription.domain.exception;

/**
 * L'opération est refusée à cause de l'état actuel du système (doublon, statut interdit,
 * souscription bloquante…).
 */
public class BusinessRuleViolationException extends DomainException {

	public BusinessRuleViolationException(DomainErrorCode errorCode, String message) {
		super(errorCode, message);
	}

}
