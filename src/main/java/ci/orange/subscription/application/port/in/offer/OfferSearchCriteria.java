package ci.orange.subscription.application.port.in.offer;

/**
 * Filtres facultatifs, combinés par un ET.
 */
public record OfferSearchCriteria(String technology, Boolean active) {
}
