package ci.orange.subscription.application.port.in.offer;

import ci.orange.subscription.domain.offer.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OfferUseCases {

	Offer create(CreateOfferCommand command);

	Page<Offer> search(OfferSearchCriteria criteria, Pageable pageable);

	Offer get(Long id);

	Offer update(Long id, UpdateOfferCommand command);

	/** Idempotent : désactiver une offre déjà inactive ne change rien. */
	Offer deactivate(Long id);

}
