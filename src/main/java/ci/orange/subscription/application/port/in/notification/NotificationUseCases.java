package ci.orange.subscription.application.port.in.notification;

import java.util.List;

/**
 * Notifications par email au client (ADR-0021).
 */
public interface NotificationUseCases {

	/**
	 * Envoie un lot de notifications dues ; un échec est retenté plus tard. Renvoie le nombre de
	 * notifications traitées, envoyées ou non : un lot complet signifie qu'il en reste peut-être.
	 */
	int dispatchDue();

	/** De la plus ancienne à la plus récente. */
	List<NotificationView> listForSubscription(Long subscriptionId);

}
