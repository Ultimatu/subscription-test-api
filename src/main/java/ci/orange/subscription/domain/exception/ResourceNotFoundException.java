package ci.orange.subscription.domain.exception;

public class ResourceNotFoundException extends DomainException {

	public ResourceNotFoundException(DomainErrorCode errorCode, String resource, Object id) {
		this(errorCode, resource, "id", id);
	}

	/** For a lookup by another key than the id, e.g. "Souscription introuvable (ND 2700000001)". */
	public ResourceNotFoundException(DomainErrorCode errorCode, String resource, String key, Object value) {
		super(errorCode, "%s introuvable (%s %s)".formatted(resource, key, value));
	}

}
