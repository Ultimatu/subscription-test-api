package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modèle de lecture d'une souscription, avec un aperçu de son client et de son offre. Construit en
 * une seule requête par l'adaptateur de persistance : les lectures ne passent pas par le modèle du
 * domaine, qui ne connaît que des identifiants.
 */
public record SubscriptionView(
		Long id,
		String reference,
		String nd,
		SubscriptionStatus status,
		String technology,
		LocalDate startDate,
		LocalDate endDate,
		ClientOverview client,
		OfferOverview offer,
		AuditInfo audit) {

	public record ClientOverview(Long id, String firstName, String lastName, String city, ClientStatus status) {
	}

	public record OfferOverview(Long id, String code, String label, BigDecimal monthlyPrice) {
	}

}
