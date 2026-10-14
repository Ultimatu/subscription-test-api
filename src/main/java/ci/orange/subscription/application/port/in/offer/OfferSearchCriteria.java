package ci.orange.subscription.application.port.in.offer;

/**
 * Optional filters, combined with AND.
 */
public record OfferSearchCriteria(String technology, Boolean active) {
}
