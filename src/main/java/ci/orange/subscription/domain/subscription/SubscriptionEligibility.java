package ci.orange.subscription.domain.subscription;

import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.domain.technology.Technology;

/**
 * Indique si un client peut souscrire à une offre. Les règles sont vérifiées dans l'ordre imposé
 * par la règle 15, et seule la première enfreinte est signalée : un client ayant une souscription
 * suspendue qui demande une technologie qu'il détient déjà reçoit l'erreur de suspension, pas celle
 * de doublon. La technologie retirée du catalogue (ADR-0013) est vérifiée avec l'offre, dont elle
 * partage la nature : un produit qui n'est plus commercialisé.
 */
public final class SubscriptionEligibility {

	private SubscriptionEligibility() {
	}

	public static void check(Client client, Offer offer, Technology technology, boolean clientHasSuspendedSubscription,
			boolean clientHasOngoingSubscriptionOnTechnology) {
		if (!client.isActive()) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_INACTIVE,
					"Le client %d est inactif : il ne peut pas souscrire de nouvelle offre".formatted(client.getId()));
		}
		if (!offer.isActive()) {
			throw new BusinessRuleViolationException(DomainErrorCode.OFFER_INACTIVE,
					"L'offre %s est retirée du catalogue : elle n'accepte plus de souscription".formatted(offer.getCode()));
		}
		technology.ensureActive();
		if (clientHasSuspendedSubscription) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_HAS_SUSPENDED_SUBSCRIPTION,
					"Le client %d a une souscription suspendue : aucune nouvelle souscription n'est possible"
							.formatted(client.getId()));
		}
		if (clientHasOngoingSubscriptionOnTechnology) {
			throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_SUBSCRIBED,
					"Le client %d a déjà une souscription en cours (PENDING ou ACTIVE) en technologie %s"
							.formatted(client.getId(), offer.getTechnology()));
		}
	}

}
