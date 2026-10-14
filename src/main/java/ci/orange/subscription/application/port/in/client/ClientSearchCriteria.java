package ci.orange.subscription.application.port.in.client;

import ci.orange.subscription.domain.client.ClientStatus;

/**
 * Filtres facultatifs, combinés par un ET. {@code search} est une recherche partielle, insensible à
 * la casse, sur le prénom, le nom ou l'email.
 */
public record ClientSearchCriteria(String city, ClientStatus status, String search) {
}
