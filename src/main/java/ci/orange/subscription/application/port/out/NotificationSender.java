package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.subscription.Attestation;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;

/**
 * Envoie une notification au client. Le canal (email) et la mise en forme du message relèvent de
 * l'infrastructure ; l'application ne fournit que les données.
 */
public interface NotificationSender {

	/** Lève une exception si l'envoi échoue : la notification sera alors retentée. */
	void send(Message message);

	/**
	 * @param reason motif de la suspension ou de la résiliation, nul sinon
	 * @param attachment attestation jointe, ou nul
	 */
	record Message(SubscriptionTransition transition, String recipient, SubscriptionView subscription, String reason,
			Attestation attachment) {
	}

}
