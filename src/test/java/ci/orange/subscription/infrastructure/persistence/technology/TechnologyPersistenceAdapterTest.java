package ci.orange.subscription.infrastructure.persistence.technology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.technology.Technology;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class TechnologyPersistenceAdapterTest {

	@Autowired
	private TechnologyRepository technologyRepository;

	@Test
	void containsTheTechnologiesOfTheSpecificationSortedByCode() {
		assertThat(technologyRepository.findAll()).extracting(Technology::getCode)
				.containsSubsequence("4G_BOX", "FTTH", "SATELLITE");
	}

	@Test
	void savesANewTechnologyWithItsAuditTrail() {
		technologyRepository.save(Technology.create("5G_BOX", "Box 5G"));

		Technology reloaded = technologyRepository.findByCode("5G_BOX").orElseThrow();

		assertThat(reloaded.getLabel()).isEqualTo("Box 5G");
		assertThat(reloaded.getAudit().createdBy()).isEqualTo("anonymous");
		assertThat(technologyRepository.existsByCode("5G_BOX")).isTrue();
	}

	@Test
	void savesTheAvailabilityOfAnExistingTechnology() {
		Technology satellite = technologyRepository.findByCode("SATELLITE").orElseThrow();
		assertThat(satellite.isActive()).isTrue();

		satellite.deactivate();
		technologyRepository.save(satellite);

		assertThat(technologyRepository.findByCode("SATELLITE").orElseThrow().isActive()).isFalse();
	}

	@Test
	void theDatabaseRejectsADuplicateCodeThatSlippedThroughTheCheck() {
		assertThatThrownBy(() -> technologyRepository.save(Technology.create("FTTH", "Doublon")))
				.isInstanceOf(BusinessRuleViolationException.class);
	}

}
