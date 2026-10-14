package ci.orange.subscription.infrastructure.persistence.offer;

import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
class OfferPersistenceAdapter implements OfferRepository {

	private static final String CODE_UNIQUE_CONSTRAINT = "uk_offer_code";

	private final OfferJpaRepository jpaRepository;

	OfferPersistenceAdapter(OfferJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Offer save(Offer offer) {
		OfferEntity entity = offer.getId() == null ? new OfferEntity() : findEntity(offer.getId());
		OfferEntityMapper.copy(offer, entity);
		try {
			return OfferEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
		}
		catch (DataIntegrityViolationException exception) {
			// Deux créations simultanées peuvent passer toutes les deux la vérification
			// existsByCode : la contrainte d'unicité tranche.
			if (exception.getCause() instanceof ConstraintViolationException violation
					&& CODE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
				throw new BusinessRuleViolationException(DomainErrorCode.OFFER_CODE_ALREADY_USED,
						"Une offre existe déjà avec le code " + offer.getCode());
			}
			throw exception;
		}
	}

	@Override
	public Optional<Offer> findById(Long id) {
		return jpaRepository.findById(id).map(OfferEntityMapper::toDomain);
	}

	@Override
	public boolean existsByCode(String code) {
		return jpaRepository.existsByCode(code);
	}

	@Override
	public Page<Offer> search(OfferSearchCriteria criteria, Pageable pageable) {
		return jpaRepository.findAll(OfferSpecifications.matching(criteria), pageable).map(OfferEntityMapper::toDomain);
	}

	private OfferEntity findEntity(Long id) {
		return jpaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.OFFER_NOT_FOUND, "Offre", id));
	}

}
