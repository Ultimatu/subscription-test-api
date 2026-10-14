package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.LocalDate;

/**
 * Filtres facultatifs, combinés par un ET. {@code city} est la ville du client (insensible à la
 * casse), {@code nd} un ND exact, et les bornes de la date de début sont incluses.
 */
public record SubscriptionSearchCriteria(SubscriptionStatus status, String technology, String city, String nd,
		LocalDate startDateFrom, LocalDate startDateTo) {
}
