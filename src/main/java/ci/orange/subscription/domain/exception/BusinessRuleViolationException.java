package ci.orange.subscription.domain.exception;

/**
 * The operation is refused because of the current state of the system (duplicate, forbidden
 * status, blocking subscription...).
 */
public class BusinessRuleViolationException extends DomainException {

	public BusinessRuleViolationException(String message) {
		super(message);
	}

}
