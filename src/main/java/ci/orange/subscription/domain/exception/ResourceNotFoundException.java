package ci.orange.subscription.domain.exception;

public class ResourceNotFoundException extends DomainException {

	public ResourceNotFoundException(DomainErrorCode errorCode, String resource, Object id) {
		this(errorCode, resource, "id", id);
	}

	/**
	 * Pour une recherche par une autre clé que l'identifiant, par exemple « Souscription
	 * introuvable (ND 2700000001) ».
	 */
	public ResourceNotFoundException(DomainErrorCode errorCode, String resource, String key, Object value) {
		super(errorCode, "%s introuvable (%s %s)".formatted(resource, key, value));
	}

}
