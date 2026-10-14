package ci.orange.subscription.domain.subscription;

/**
 * One status change of a subscription. {@code from} is null for the creation.
 */
public record StatusChange(SubscriptionStatus from, SubscriptionStatus to, String reason) {
}
