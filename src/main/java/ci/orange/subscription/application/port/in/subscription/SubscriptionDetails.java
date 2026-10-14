package ci.orange.subscription.application.port.in.subscription;

import java.util.List;

/**
 * Une souscription avec tout son historique des statuts, du changement le plus ancien au plus
 * récent.
 */
public record SubscriptionDetails(SubscriptionView subscription, List<StatusHistoryEntry> history) {
}
