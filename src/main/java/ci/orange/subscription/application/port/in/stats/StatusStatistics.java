package ci.orange.subscription.application.port.in.stats;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.LocalDate;
import java.util.Map;

/**
 * Nombre de souscriptions pour chaque statut (zéro compris) sur une période. {@code total} est le
 * nombre de souscriptions distinctes comptées : avec {@link PeriodBasis#STATUS_CHANGE}, une
 * souscription peut apparaître sous plusieurs statuts mais ne compte qu'une fois dans le total.
 */
public record StatusStatistics(LocalDate from, LocalDate to, PeriodBasis basedOn,
		Map<SubscriptionStatus, Long> subscriptionsByStatus, long total) {
}
