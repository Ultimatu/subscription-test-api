package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.technology.CreateTechnologyCommand;
import ci.orange.subscription.application.port.in.technology.TechnologyUseCases;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.technology.Technology;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TechnologyService implements TechnologyUseCases {

	private final TechnologyRepository technologyRepository;
	private final AuditTrail auditTrail;

	public TechnologyService(TechnologyRepository technologyRepository, AuditTrail auditTrail) {
		this.technologyRepository = technologyRepository;
		this.auditTrail = auditTrail;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Technology> list(Boolean active) {
		return technologyRepository.findAll().stream()
				.filter(technology -> active == null || technology.isActive() == active)
				.toList();
	}


	@Override
	@Transactional(readOnly = true)
	public Technology get(String code) {
		return technologyRepository.findByCode(code)
				.orElseThrow(
						() -> new ResourceNotFoundException(DomainErrorCode.TECHNOLOGY_NOT_FOUND, "Technologie", code));
	}
	@Override
	public Technology deactivate(String code) {
		Technology technology = get(code);
		if (!technology.isActive()) {
			return technology;
		}
		technology.deactivate();
		Technology saved = technologyRepository.save(technology);
		auditTrail.record(new AuditTrail.Entry(AuditAction.TECHNOLOGY_DEACTIVATED, AuditResource.TECHNOLOGY, code, null,
				"Technologie " + code + " retirée du catalogue", Map.of()));
		return saved;
	}

	@Override
	public Technology activate(String code) {
		Technology technology = get(code);
		if (technology.isActive()) {
			return technology;
		}
		technology.activate();
		Technology saved = technologyRepository.save(technology);
		auditTrail.record(new AuditTrail.Entry(AuditAction.TECHNOLOGY_ACTIVATED, AuditResource.TECHNOLOGY, code, null,
				"Technologie " + code + " remise au catalogue", Map.of()));
		return saved;
	}


	@Override
	public Technology create(CreateTechnologyCommand command) {
		Technology technology = Technology.create(command.code(), command.label());
		if (technologyRepository.existsByCode(technology.getCode())) {
			throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS,
					"La technologie " + technology.getCode() + " existe déjà");
		}
		Technology saved = technologyRepository.save(technology);
		auditTrail.record(new AuditTrail.Entry(AuditAction.TECHNOLOGY_CREATED, AuditResource.TECHNOLOGY,
				saved.getCode(), null, "Technologie " + saved.getCode() + " ajoutée au catalogue",
				Map.of("label", saved.getLabel())));
		return saved;
	}

}
