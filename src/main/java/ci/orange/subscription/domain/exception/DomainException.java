package ci.orange.subscription.domain.exception;

/**
 * Base class of business errors. The domain only describes what went wrong; the web adapter
 * decides how it is exposed (HTTP status, error format).
 */
public abstract class DomainException extends RuntimeException {

	protected DomainException(String message) {
		super(message);
	}

}
