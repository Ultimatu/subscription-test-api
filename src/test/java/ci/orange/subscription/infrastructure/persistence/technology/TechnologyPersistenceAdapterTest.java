package ci.orange.subscription.infrastructure.persistence.technology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
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
	void findsATechnologyByItsLabelIgnoringCaseAndAccents() {
		technologyRepository.save(Technology.create("TEL_FIXE", "Téléphonie fixe"));

		assertThat(technologyRepository.findByLabel("TELEPHONIE FIXE")).map(Technology::getCode).contains("TEL_FIXE");
		assertThat(technologyRepository.findByLabel("Téléphonie mobile")).isEmpty();
	}

	@Test
	void theDatabaseRejectsADuplicateCodeThatSlippedThroughTheCheck() {
		assertThatThrownBy(() -> technologyRepository.save(Technology.create("FTTH", "Doublon")))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS);
	}

	@Test
	void theDatabaseRejectsADuplicateLabelThatSlippedThroughTheCheck() {
		technologyRepository.save(Technology.create("TEL_FIXE", "Téléphonie fixe"));

		// Même libellé, à la casse et aux accents près : l'index unique de V13 les compare ainsi
		assertThatThrownBy(() -> technologyRepository.save(Technology.create("TELEPHONIE", "TELEPHONIE FIXE")))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_LABEL_ALREADY_USED);
	}

}
