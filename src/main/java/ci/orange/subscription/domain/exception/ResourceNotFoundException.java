package ci.orange.subscription.domain.exception;

public class ResourceNotFoundException extends DomainException {

	public ResourceNotFoundException(String resource, Object id) {
		super("%s introuvable (id %s)".formatted(resource, id));
	}

}
