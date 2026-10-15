package ci.orange.subscription.infrastructure.persistence.technology;

import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.technology.Technology;
import ci.orange.subscription.infrastructure.persistence.entity.TechnologyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class TechnologyPersistenceAdapter implements TechnologyRepository {

	private final TechnologyJpaRepository jpaRepository;

	TechnologyPersistenceAdapter(TechnologyJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public List<Technology> findAll() {
		return jpaRepository.findAll(Sort.by("code")).stream().map(TechnologyPersistenceAdapter::toDomain).toList();
	}

	@Override
	public Optional<Technology> findByCode(String code) {
		return jpaRepository.findById(code).map(TechnologyPersistenceAdapter::toDomain);
	}

	@Override
	public boolean existsByCode(String code) {
		return jpaRepository.existsById(code);
	}

	/** Une technologie déjà enregistrée (lue en base, donc avec son audit) est mise à jour. */
	@Override
	public Technology save(Technology technology) {
		TechnologyEntity entity = technology.getAudit() == null ? new TechnologyEntity()
				: jpaRepository.findById(technology.getCode()).orElseThrow();
		entity.setCode(technology.getCode());
		entity.setLabel(technology.getLabel());
		entity.setActive(technology.isActive());
		try {
			return toDomain(jpaRepository.saveAndFlush(entity));
		}
		catch (DataIntegrityViolationException exception) {
			// Deux créations simultanées du même code : la clé primaire tranche.
			throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS,
					"La technologie " + technology.getCode() + " existe déjà");
		}
	}

	private static Technology toDomain(TechnologyEntity entity) {
		return Technology.restore(entity.getCode(), entity.getLabel(), entity.isActive(), entity.toAuditInfo());
	}

}
