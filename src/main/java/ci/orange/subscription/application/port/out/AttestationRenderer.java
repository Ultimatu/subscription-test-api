package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.domain.client.Client;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Met en page l'attestation d'une souscription. Le format (PDF) et la mise en page relèvent de
 * l'infrastructure ; l'application ne fournit que les données.
 */
public interface AttestationRenderer {

	byte[] render(Content content);

	/**
	 * @param holder le titulaire, pour ses coordonnées complètes
	 * @param technologyLabel libellé de la technologie, par exemple « Fibre optique »
	 * @param generatedAt date et heure de génération, dans le fuseau de l'application
	 */
	record Content(SubscriptionView subscription, List<StatusHistoryEntry> history, Client holder,
			String technologyLabel, ZonedDateTime generatedAt) {
	}

}
