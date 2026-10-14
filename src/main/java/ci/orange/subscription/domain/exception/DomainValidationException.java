package ci.orange.subscription.domain.exception;

/**
 * Les données fournies enfreignent un invariant métier (valeur manquante, format incorrect, date
 * passée…).
 */
public class DomainValidationException extends DomainException {

	public DomainValidationException(DomainErrorCode errorCode, String message) {
		super(errorCode, message);
	}

}
