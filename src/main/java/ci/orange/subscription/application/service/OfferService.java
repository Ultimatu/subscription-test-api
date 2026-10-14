package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.offer.CreateOfferCommand;
import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.in.offer.OfferUseCases;
import ci.orange.subscription.application.port.in.offer.UpdateOfferCommand;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OfferService implements OfferUseCases {

	private final OfferRepository offerRepository;
	private final TechnologyRepository technologyRepository;

	/** Invalid data (400) is reported before conflicts (409). */
	@Override
	public Offer create(CreateOfferCommand command) {
		Offer offer = Offer.create(command.code(), command.label(), command.technology(), command.monthlyPrice());
		if (!technologyRepository.existsByCode(offer.getTechnology())) {
			throw new DomainValidationException(DomainErrorCode.UNKNOWN_TECHNOLOGY,
					"Technologie inconnue : " + offer.getTechnology() + " (voir GET /api/technologies)");
		}
		if (offerRepository.existsByCode(offer.getCode())) {
			throw new BusinessRuleViolationException(DomainErrorCode.OFFER_CODE_ALREADY_USED,
					"Une offre existe déjà avec le code " + offer.getCode());
		}
		return offerRepository.save(offer);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Offer> search(OfferSearchCriteria criteria, Pageable pageable) {
		return offerRepository.search(criteria, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public Offer get(Long id) {
		return findOffer(id);
	}

	@Override
	public Offer update(Long id, UpdateOfferCommand command) {
		Offer offer = findOffer(id);
		offer.update(command.label(), command.monthlyPrice());
		return offerRepository.save(offer);
	}

	@Override
	public Offer deactivate(Long id) {
		Offer offer = findOffer(id);
		if (!offer.isActive()) {
			return offer;
		}
		offer.deactivate();
		return offerRepository.save(offer);
	}

	private Offer findOffer(Long id) {
		return offerRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.OFFER_NOT_FOUND, "Offre", id));
	}

}
