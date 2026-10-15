package ci.orange.subscription.application.port.in.subscription;

/**
 * Attestation de souscription au format PDF (ADR-0019).
 */
public interface AttestationUseCases {

	/**
	 * Générée à la demande, à partir de l'état actuel de la souscription : aucun document n'est
	 * stocké. Une souscription résiliée donne une attestation de résiliation.
	 */
	Attestation generate(Long subscriptionId);

}
