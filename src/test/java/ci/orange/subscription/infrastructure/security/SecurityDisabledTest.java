package ci.orange.subscription.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Avec app.security.enabled=false, toute l'API répond sans jeton, y compris ce qui est réservé aux
 * administrateurs quand la sécurité est active.
 */
@IntegrationTest(properties = "app.security.enabled=false")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SecurityDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theWholeApiIsOpen() throws Exception {
		mockMvc.perform(get("/api/clients")).andExpect(status().isOk());
		mockMvc.perform(get("/api/stats/subscriptions-by-city")).andExpect(status().isOk());
	}

	@Test
	void warnsAtStartupThatTheApiIsOpen(CapturedOutput output) {
		assertThat(output).contains("WARN").contains("Security is DISABLED");
	}

}
