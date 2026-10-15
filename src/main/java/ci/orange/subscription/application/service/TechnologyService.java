package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.technology.CreateTechnologyCommand;
import ci.orange.subscription.application.port.in.technology.TechnologyUseCases;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.technology.Technology;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TechnologyService implements TechnologyUseCases {

	private final TechnologyRepository technologyRepository;

	public TechnologyService(TechnologyRepository technologyRepository) {
		this.technologyRepository = technologyRepository;
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
		technology.deactivate();
		return technologyRepository.save(technology);
	}

	@Override
	public Technology activate(String code) {
		Technology technology = get(code);
		technology.activate();
		return technologyRepository.save(technology);
	}


	@Override
	public Technology create(CreateTechnologyCommand command) {
		Technology technology = Technology.create(command.code(), command.label());
		if (technologyRepository.existsByCode(technology.getCode())) {
			throw new BusinessRuleViolationException(DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS,
					"La technologie " + technology.getCode() + " existe déjà");
		}
		return technologyRepository.save(technology);
	}

}
