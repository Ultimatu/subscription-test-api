package ci.orange.subscription.application.port.in.subscription;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.LocalDate;

/**
 * Optional filters, combined with AND. {@code city} is the client's city (case-insensitive),
 * {@code nd} an exact ND, and the start date bounds are inclusive.
 */
public record SubscriptionSearchCriteria(SubscriptionStatus status, String technology, String city, String nd,
		LocalDate startDateFrom, LocalDate startDateTo) {
}
