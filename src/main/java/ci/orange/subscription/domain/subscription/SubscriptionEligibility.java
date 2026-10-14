package ci.orange.subscription.domain.subscription;

import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.offer.Offer;

/**
 * Whether a client may subscribe to an offer. The rules are checked in the order imposed by rule
 * 15, and the first broken one is reported: a client with a suspended subscription asking for a
 * technology they already hold gets the suspension error, not the duplicate one.
 */
public final class SubscriptionEligibility {

	private SubscriptionEligibility() {
	}

	public static void check(Client client, Offer offer, boolean clientHasSuspendedSubscription,
			boolean clientHasOngoingSubscriptionOnTechnology) {
		if (!client.isActive()) {
			throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_INACTIVE,
					"Le client %d est inactif : il ne peut pas souscrire de nouvelle offre".formatted(client.getId()));
		}
		if (!offer.isActive()) {
			throw new BusinessRuleViolationException(DomainErrorCode.OFFER_INACTIVE,
					"L'offre %s est retirée du catalogue : elle n'accepte plus de souscription".formatted(offer.getCode()));
		}
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
