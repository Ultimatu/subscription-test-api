package ci.orange.subscription.application.port.out;

import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.domain.offer.Offer;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OfferRepository {

	Offer save(Offer offer);

	Optional<Offer> findById(Long id);

	boolean existsByCode(String code);

	Page<Offer> search(OfferSearchCriteria criteria, Pageable pageable);

}
