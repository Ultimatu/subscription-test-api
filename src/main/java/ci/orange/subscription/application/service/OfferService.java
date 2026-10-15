package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.offer.CreateOfferCommand;
import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.in.offer.OfferUseCases;
import ci.orange.subscription.application.port.in.offer.UpdateOfferCommand;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OfferService implements OfferUseCases {

	private final OfferRepository offerRepository;
	private final TechnologyRepository technologyRepository;
	private final AuditTrail auditTrail;

	public OfferService(OfferRepository offerRepository, TechnologyRepository technologyRepository,
			AuditTrail auditTrail) {
		this.auditTrail = auditTrail;
		this.offerRepository = offerRepository;
		this.technologyRepository = technologyRepository;
	}

	/** Les données invalides (400) sont signalées avant les conflits (409). */
	@Override
	public Offer create(CreateOfferCommand command) {
		Offer offer = Offer.create(command.code(), command.label(), command.technology(), command.monthlyPrice());
		technologyRepository.findByCode(offer.getTechnology())
				.orElseThrow(() -> new DomainValidationException(DomainErrorCode.UNKNOWN_TECHNOLOGY,
						"Technologie inconnue : " + offer.getTechnology() + " (voir GET /api/technologies)"))
				.ensureActive();
		if (offerRepository.existsByCode(offer.getCode())) {
			throw new BusinessRuleViolationException(DomainErrorCode.OFFER_CODE_ALREADY_USED,
					"Une offre existe déjà avec le code " + offer.getCode());
		}
		Offer saved = offerRepository.save(offer);
		auditTrail.record(new AuditTrail.Entry(AuditAction.OFFER_CREATED, AuditResource.OFFER, saved.getId(), null,
				"Offre " + saved.getCode() + " créée",
				Map.of("code", saved.getCode(), "technology", saved.getTechnology(), "monthlyPrice",
						saved.getMonthlyPrice())));
		return saved;
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
		String labelBefore = offer.getLabel();
		BigDecimal priceBefore = offer.getMonthlyPrice();
		offer.update(command.label(), command.monthlyPrice());
		Offer saved = offerRepository.save(offer);
		Changes changes = new Changes().track("label", labelBefore, saved.getLabel())
				.track("monthlyPrice", priceBefore, saved.getMonthlyPrice());
		if (!changes.isEmpty()) {
			auditTrail.record(new AuditTrail.Entry(AuditAction.OFFER_UPDATED, AuditResource.OFFER, saved.getId(), null,
					"Offre " + saved.getCode() + " modifiée", changes.asMap()));
		}
		return saved;
	}

	@Override
	public Offer deactivate(Long id) {
		Offer offer = findOffer(id);
		if (!offer.isActive()) {
			return offer;
		}
		offer.deactivate();
		Offer saved = offerRepository.save(offer);
		auditTrail.record(new AuditTrail.Entry(AuditAction.OFFER_DEACTIVATED, AuditResource.OFFER, saved.getId(), null,
				"Offre " + saved.getCode() + " retirée du catalogue", Map.of()));
		return saved;
	}

	private Offer findOffer(Long id) {
		return offerRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.OFFER_NOT_FOUND, "Offre", id));
	}

}
