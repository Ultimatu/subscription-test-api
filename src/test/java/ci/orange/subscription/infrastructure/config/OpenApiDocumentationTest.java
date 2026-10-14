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
 * La description OpenAPI reste à son emplacement standard (hors du préfixe /api) et documente les
 * vrais codes de statut et corps d'erreur.
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
				.andExpect(jsonPath("$.paths['/api/subscriptions'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/api/subscriptions/terminate'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/api/subscriptions/by-nd/{nd}'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/clients/{clientId}/subscriptions'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/stats/subscriptions-by-city'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/stats/subscriptions-by-technology'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/stats/subscriptions-by-status'].get.parameters[*].name")
						.value(hasItems("from", "to", "basedOn")))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/api/clients'].get.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/stats/subscriptions-by-city'].get.responses['403']").exists())
				.andExpect(jsonPath("$.components.schemas.Problem.properties.code").exists())
				.andExpect(jsonPath("$.components.schemas.Problem.properties.requestId").exists());
	}

}
