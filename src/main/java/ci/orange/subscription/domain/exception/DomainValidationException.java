package ci.orange.subscription.domain.exception;

/**
 * The data provided breaks a business invariant (missing value, wrong format, date in the past...).
 */
public class DomainValidationException extends DomainException {

	public DomainValidationException(DomainErrorCode errorCode, String message) {
		super(errorCode, message);
	}

}
