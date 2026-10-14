package ci.orange.subscription.domain.exception;

import java.util.Objects;

/**
 * Base class of business errors. The domain only describes what went wrong, with a stable code;
 * the web adapter decides how it is exposed (HTTP status, error format).
 */
public abstract class DomainException extends RuntimeException {

	private final DomainErrorCode errorCode;

	protected DomainException(DomainErrorCode errorCode, String message) {
		super(message);
		this.errorCode = Objects.requireNonNull(errorCode);
	}

	public DomainErrorCode getErrorCode() {
		return errorCode;
	}

}
