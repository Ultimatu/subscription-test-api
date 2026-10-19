package ci.orange.subscription.infrastructure.persistence.technology;

import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.technology.Technology;
import ci.orange.subscription.infrastructure.persistence.TextSearch;
import ci.orange.subscription.infrastructure.persistence.entity.TechnologyEntity;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class TechnologyPersistenceAdapter implements TechnologyRepository {

	private static final String CODE_PRIMARY_KEY = "technology_pkey";
	private static final String LABEL_UNIQUE_CONSTRAINT = "uk_technology_label";

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

	@Override
	public Optional<Technology> findByLabel(String label) {
		return jpaRepository
				.findOne((root, query, builder) -> TextSearch.equal(builder, root.get("label"), label))
				.map(TechnologyPersistenceAdapter::toDomain);
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
			// Deux créations simultanées peuvent passer toutes les deux les vérifications du
			// service : la clé primaire (code) ou l'index unique du libellé tranche.
			if (violates(exception, CODE_PRIMARY_KEY)) {
				throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS,
						"La technologie " + technology.getCode() + " existe déjà");
			}
			if (violates(exception, LABEL_UNIQUE_CONSTRAINT)) {
				throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_LABEL_ALREADY_USED,
						"Le libellé « " + technology.getLabel() + " » est déjà utilisé par une autre technologie");
			}
			throw exception;
		}
	}

	private static boolean violates(DataIntegrityViolationException exception, String constraint) {
		return exception.getCause() instanceof ConstraintViolationException violation
				&& constraint.equals(violation.getConstraintName());
	}

	private static Technology toDomain(TechnologyEntity entity) {
		return Technology.restore(entity.getCode(), entity.getLabel(), entity.isActive(), entity.toAuditInfo());
	}

}
