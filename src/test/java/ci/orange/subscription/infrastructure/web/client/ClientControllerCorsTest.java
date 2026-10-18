package ci.orange.subscription.infrastructure.web.client;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.client.ClientDeletionUseCases;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.infrastructure.web.WebAdapterTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebAdapterTest(ClientController.class)
@TestPropertySource(properties = "app.cors.allowed-origins=" + ClientControllerCorsTest.BACK_OFFICE)
class ClientControllerCorsTest {

	static final String BACK_OFFICE = "https://back-office.example";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClientUseCases clientUseCases;

	@MockitoBean
	private ClientDeletionUseCases clientDeletionUseCases;

	@Test
	void letsTheBackOfficeSendAnAuthenticatedRequest() throws Exception {
		mockMvc.perform(options("/api/clients")
				.header(HttpHeaders.ORIGIN, BACK_OFFICE)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, BACK_OFFICE))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
	}

	@Test
	void exposesTheHeadersTheBackOfficeReads() throws Exception {
		when(clientUseCases.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

		mockMvc.perform(get("/api/clients").header(HttpHeaders.ORIGIN, BACK_OFFICE))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, BACK_OFFICE))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
						allOf(containsString("X-Request-Id"), containsString("Content-Disposition"))));
	}

	@Test
	void rejectsAnyOtherOrigin() throws Exception {
		mockMvc.perform(options("/api/clients")
				.header(HttpHeaders.ORIGIN, "https://unknown.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

}
