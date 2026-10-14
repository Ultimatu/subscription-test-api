package ci.orange.subscription.infrastructure.config;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI description stays at its standard location (outside the /api prefix) and documents
 * the real status codes and error bodies.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void servesTheSwaggerUi() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	void documentsTheApiUnderItsBasePath() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Subscription API"))
				.andExpect(jsonPath("$.paths['/api/clients'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/api/clients'].post.responses['409'].content['application/problem+json']")
						.exists())
				.andExpect(jsonPath("$.paths['/api/clients/{id}'].delete.responses['204']").exists())
				.andExpect(jsonPath("$.paths['/api/clients/{id}'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/clients/{id}'].get.responses['404']").exists())
				.andExpect(jsonPath("$.paths['/api/clients'].get.parameters[*].name")
						.value(hasItems("city", "status", "search", "page", "size", "sort")))
				.andExpect(jsonPath("$.paths['/api/technologies'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/api/offers/{id}/deactivate'].patch.responses['200']").exists())
				.andExpect(jsonPath("$.components.schemas.Problem.properties.code").exists())
				.andExpect(jsonPath("$.components.schemas.Problem.properties.requestId").exists());
	}

}
