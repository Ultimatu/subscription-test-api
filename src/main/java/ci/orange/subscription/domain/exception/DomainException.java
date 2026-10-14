package ci.orange.subscription.domain.exception;

import java.util.Objects;

/**
 * Classe de base des erreurs métier. Le domaine décrit seulement ce qui ne va pas, avec un code
 * stable ; l'adaptateur web décide comment l'exposer (statut HTTP, format d'erreur).
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
