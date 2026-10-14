package ci.orange.subscription.infrastructure.persistence.technology;

import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.technology.Technology;
import ci.orange.subscription.infrastructure.persistence.entity.TechnologyEntity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TechnologyPersistenceAdapter implements TechnologyRepository {

	private final TechnologyJpaRepository jpaRepository;

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

	@Override
	public Technology save(Technology technology) {
		TechnologyEntity entity = new TechnologyEntity();
		entity.setCode(technology.getCode());
		entity.setLabel(technology.getLabel());
		try {
			return toDomain(jpaRepository.saveAndFlush(entity));
		}
		catch (DataIntegrityViolationException exception) {
			// Two concurrent creations of the same code: the primary key decides.
			throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS,
					"La technologie " + technology.getCode() + " existe déjà");
		}
	}

	private static Technology toDomain(TechnologyEntity entity) {
		return Technology.restore(entity.getCode(), entity.getLabel(), entity.toAuditInfo());
	}

}
