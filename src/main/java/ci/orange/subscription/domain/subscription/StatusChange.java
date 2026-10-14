package ci.orange.subscription.domain.subscription;

/**
 * Un changement de statut d'une souscription. {@code from} est nul pour la création.
 */
public record StatusChange(SubscriptionStatus from, SubscriptionStatus to, String reason) {
}
