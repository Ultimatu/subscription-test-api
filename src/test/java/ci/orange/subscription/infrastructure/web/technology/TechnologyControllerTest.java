package ci.orange.subscription.infrastructure.web.technology;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.technology.CreateTechnologyCommand;
import ci.orange.subscription.application.port.in.technology.TechnologyUseCases;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.technology.Technology;
import ci.orange.subscription.infrastructure.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TechnologyController.class)
@Import(SecurityConfig.class)
class TechnologyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TechnologyUseCases technologyUseCases;

	@Test
	void listsTheCatalogue() throws Exception {
		when(technologyUseCases.list()).thenReturn(List.of(Technology.restore("4G_BOX", "Box 4G", null),
				Technology.restore("FTTH", "Fibre optique", null)));

		mockMvc.perform(get("/api/technologies"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].code").value("4G_BOX"))
				.andExpect(jsonPath("$[1].label").value("Fibre optique"));
	}

	@Test
	void getsATechnology() throws Exception {
		when(technologyUseCases.get("FTTH")).thenReturn(Technology.restore("FTTH", "Fibre optique", null));

		mockMvc.perform(get("/api/technologies/FTTH"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("FTTH"));
	}

	@Test
	void reportsAnUnknownTechnology() throws Exception {
		when(technologyUseCases.get("5G_BOX")).thenThrow(new ResourceNotFoundException("Technologie", "5G_BOX"));

		mockMvc.perform(get("/api/technologies/5G_BOX")).andExpect(status().isNotFound());
	}

	@Test
	void createsATechnology() throws Exception {
		when(technologyUseCases.create(any())).thenReturn(Technology.restore("5G_BOX", "Box 5G", null));

		mockMvc.perform(post("/api/technologies").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"5G_BOX\", \"label\": \"Box 5G\" }"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/technologies/5G_BOX")))
				.andExpect(jsonPath("$.code").value("5G_BOX"));

		verify(technologyUseCases).create(new CreateTechnologyCommand("5G_BOX", "Box 5G"));
	}

	@Test
	void rejectsAnInvalidCode() throws Exception {
		mockMvc.perform(post("/api/technologies").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"5g-box\", \"label\": \"Box 5G\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("code"));

		verifyNoInteractions(technologyUseCases);
	}

	@Test
	void reportsAnExistingCodeAsAConflict() throws Exception {
		when(technologyUseCases.create(any()))
				.thenThrow(new BusinessRuleViolationException("La technologie FTTH existe déjà"));

		mockMvc.perform(post("/api/technologies").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"code\": \"FTTH\", \"label\": \"Fibre\" }"))
				.andExpect(status().isConflict());
	}

}
