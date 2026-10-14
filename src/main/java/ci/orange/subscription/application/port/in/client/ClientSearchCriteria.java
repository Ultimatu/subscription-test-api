package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.client.ClientStatus;

/**
 * Optional filters, combined with AND. {@code search} is a case-insensitive partial match on the
 * first name, the last name or the email.
 */
public record ClientSearchCriteria(String city, ClientStatus status, String search) {
}
