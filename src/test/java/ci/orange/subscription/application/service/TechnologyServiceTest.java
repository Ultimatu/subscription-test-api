package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.technology.CreateTechnologyCommand;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.technology.Technology;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TechnologyServiceTest {

	@Mock
	private TechnologyRepository technologyRepository;

	@InjectMocks
	private TechnologyService technologyService;

	@Test
	void listsTheCatalogue() {
		List<Technology> catalogue = List.of(Technology.restore("FTTH", "Fibre optique", null));
		when(technologyRepository.findAll()).thenReturn(catalogue);

		assertThat(technologyService.list()).isSameAs(catalogue);
	}

	@Test
	void getsATechnology() {
		Technology ftth = Technology.restore("FTTH", "Fibre optique", null);
		when(technologyRepository.findByCode("FTTH")).thenReturn(Optional.of(ftth));

		assertThat(technologyService.get("FTTH")).isSameAs(ftth);
	}

	@Test
	void reportsAnUnknownTechnology() {
		when(technologyRepository.findByCode("5G_BOX")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> technologyService.get("5G_BOX"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_NOT_FOUND)
				.hasMessageContaining("5G_BOX");
	}

	@Test
	void createsATechnology() {
		when(technologyRepository.existsByCode("5G_BOX")).thenReturn(false);
		when(technologyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Technology technology = technologyService.create(new CreateTechnologyCommand("5G_BOX", "Box 5G"));

		assertThat(technology.getCode()).isEqualTo("5G_BOX");
		assertThat(technology.getLabel()).isEqualTo("Box 5G");
	}

	@Test
	void refusesAnExistingCode() {
		when(technologyRepository.existsByCode("FTTH")).thenReturn(true);

		assertThatThrownBy(() -> technologyService.create(new CreateTechnologyCommand("FTTH", "Fibre")))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_ALREADY_EXISTS)
				.hasMessageContaining("FTTH");
		verify(technologyRepository, never()).save(any());
	}

}
