package ci.orange.subscription.domain.exception;

public class ResourceNotFoundException extends DomainException {

	public ResourceNotFoundException(DomainErrorCode errorCode, String resource, Object id) {
		super(errorCode, "%s introuvable (id %s)".formatted(resource, id));
	}

}
