package ci.orange.subscription.infrastructure.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matrice des droits de bout en bout, avec de vrais jetons émis par POST /api/auth/login et les
 * comptes de démonstration de la migration V8. Chaque test est annulé à la fin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

	private static final String NEW_CLIENT = """
			{ "firstName": "Awa", "lastName": "Koné", "email": "awa.securite@mail.ci",
			  "phoneNumberClient": "0701020304", "city": "Abidjan" }
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	void documentationAndHealthArePublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void theApiRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/clients"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.instance").value("/api/clients"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void rejectsAForgedToken() throws Exception {
		mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void rejectsAnExpiredToken() throws Exception {
		Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
		String expired = token("subscription-api", twoHoursAgo, twoHoursAgo.plus(1, ChronoUnit.HOURS));

		mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void rejectsATokenFromAnotherIssuer() throws Exception {
		Instant now = Instant.now();
		String foreign = token("another-application", now, now.plus(1, ChronoUnit.HOURS));

		mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void logsInAndUsesTheToken() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"admin\", \"password\": \"Admin@2026\" }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.roles[0]").value("ADMIN"))
				.andExpect(jsonPath("$.accessToken").isNotEmpty());

		mockMvc.perform(get("/api/stats/subscriptions-by-city").header(HttpHeaders.AUTHORIZATION, bearer("admin")))
				.andExpect(status().isOk());
	}

	@Test
	void givesTheSameAnswerForAWrongPasswordAndAnUnknownAccount() throws Exception {
		for (String credentials : List.of("{ \"username\": \"admin\", \"password\": \"wrong\" }",
				"{ \"username\": \"nobody\", \"password\": \"Admin@2026\" }")) {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
					.andExpect(jsonPath("$.detail").value("Identifiant ou mot de passe incorrect"));
		}
	}

	@Test
	void aConseillerManagesClientsAndSubscriptionsButNeitherDeletesNorPilots() throws Exception {
		String conseiller = bearer("conseiller");
		String location = mockMvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, conseiller)
				.contentType(MediaType.APPLICATION_JSON).content(NEW_CLIENT))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.createdBy").value("conseiller"))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
		String clientPath = location.substring(location.indexOf("/api/"));

		mockMvc.perform(get("/api/offers").header(HttpHeaders.AUTHORIZATION, conseiller)).andExpect(status().isOk());
		mockMvc.perform(delete(clientPath).header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/stats/subscriptions-by-technology").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/offers").header(HttpHeaders.AUTHORIZATION, conseiller)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/offers/1/deactivate").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/technologies").header(HttpHeaders.AUTHORIZATION, conseiller)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/technologies/FTTH/deactivate").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/clients/export").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/clients/deleted").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/audit-events").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/clients/1/purge").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/subscriptions/export").header(HttpHeaders.AUTHORIZATION, conseiller))
				.andExpect(status().isForbidden());
	}

	@Test
	void anAdministratorCanExport() throws Exception {
		String admin = bearer("admin");

		mockMvc.perform(get("/api/clients/export").header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isOk())
				.andExpect(content().contentType("text/csv;charset=UTF-8"));
		mockMvc.perform(get("/api/subscriptions/export").header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/audit-events").header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isOk());
	}

	@Test
	void anAdministratorCanDelete() throws Exception {
		String admin = bearer("admin");
		String location = mockMvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, admin)
				.contentType(MediaType.APPLICATION_JSON).content(NEW_CLIENT))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mockMvc.perform(delete(location.substring(location.indexOf("/api/"))).header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isNoContent());
	}

	private String bearer(String username) throws Exception {
		String password = username.equals("admin") ? "Admin@2026" : "Conseiller@2026";
		String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"%s\", \"password\": \"%s\" }".formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(response, "$.accessToken");
	}

	/** Jeton signé avec la bonne clé, mais dont l'émetteur ou les dates sont choisis par le test. */
	private String token(String issuer, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("admin").issuedAt(issuedAt)
				.expiresAt(expiresAt).claim(TokenService.ROLES_CLAIM, List.of("ADMIN")).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

}
