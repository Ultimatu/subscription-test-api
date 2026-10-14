package ci.orange.subscription.infrastructure.web.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.application.port.in.client.ClientUseCases;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.client.UpdateClientCommand;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.common.AuditInfo;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
class ClientControllerTest {

	private static final String VALID_CLIENT = """
			{
			  "firstName": "Awa",
			  "lastName": "Koné",
			  "email": "awa@mail.ci",
			  "phoneNumberClient": "0701020304",
			  "city": "Abidjan"
			}
			""";

	private static final String VALID_UPDATE = """
			{
			  "firstName": "Aya",
			  "lastName": "Koné",
			  "phoneNumberClient": "0501020304",
			  "city": "Bouaké",
			  "status": "INACTIVE"
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClientUseCases clientUseCases;

	@Test
	void createsAClient() throws Exception {
		when(clientUseCases.create(any())).thenReturn(client(ClientStatus.ACTIVE));

		mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/clients/1")))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.email").value("awa@mail.ci"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").value("2026-10-14T10:00:00Z"))
				.andExpect(jsonPath("$.createdBy").value("conseiller"));

		verify(clientUseCases)
				.create(new CreateClientCommand("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan"));
	}

	@Test
	void ignoresAStatusSentAtCreation() throws Exception {
		when(clientUseCases.create(any())).thenReturn(client(ClientStatus.ACTIVE));
		String withStatus = VALID_CLIENT.replace("\"city\"", "\"status\": \"INACTIVE\", \"city\"");

		mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(withStatus))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void rejectsInvalidFieldsWithTheirMessages() throws Exception {
		String invalid = """
				{ "firstName": "", "lastName": "Koné", "email": "not-an-email",
				  "phoneNumberClient": "0201020304", "city": "Abidjan" }
				""";

		mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(invalid))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Requête invalide"))
				.andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[?(@.field == 'phoneNumberClient')].message")
						.value("doit comporter dix chiffres et commencer par 01, 05 ou 07"))
				.andExpect(jsonPath("$.errors[?(@.field == 'firstName')].message").value("ne doit pas être vide"));

		verifyNoInteractions(clientUseCases);
	}

	@Test
	void reportsADuplicateEmailAsAConflict() throws Exception {
		when(clientUseCases.create(any()))
				.thenThrow(new BusinessRuleViolationException("Un client existe déjà avec l'email awa@mail.ci"));

		mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Règle métier non respectée"))
				.andExpect(jsonPath("$.detail").value("Un client existe déjà avec l'email awa@mail.ci"));
	}

	@Test
	void rejectsAMalformedBody() throws Exception {
		mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Le corps de la requête est illisible ou mal formé"));
	}

	@Test
	void searchesWithFiltersPaginationAndSort() throws Exception {
		when(clientUseCases.search(any(), any())).thenReturn(
				new PageImpl<>(List.of(client(ClientStatus.ACTIVE)), PageRequest.of(1, 5), 6));

		mockMvc.perform(get("/api/clients").param("city", "Abidjan").param("status", "ACTIVE").param("search", "kon")
				.param("page", "1").param("size", "5").param("sort", "lastName,desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].email").value("awa@mail.ci"))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(5))
				.andExpect(jsonPath("$.totalElements").value(6))
				.andExpect(jsonPath("$.totalPages").value(2));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(clientUseCases).search(eq(new ClientSearchCriteria("Abidjan", ClientStatus.ACTIVE, "kon")),
				pageable.capture());
		assertThat(pageable.getValue()).isEqualTo(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "lastName")));
	}

	@Test
	void appliesDefaultPaginationAndCapsThePageSize() throws Exception {
		when(clientUseCases.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

		mockMvc.perform(get("/api/clients").param("size", "500")).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(clientUseCases).search(eq(new ClientSearchCriteria(null, null, null)), pageable.capture());
		assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 100, Sort.by("id")));
	}

	@Test
	void rejectsAnUnknownStatusFilter() throws Exception {
		mockMvc.perform(get("/api/clients").param("status", "DELETED"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valeur invalide pour le paramètre status : DELETED"));
	}

	@Test
	void rejectsAnUnknownSortProperty() throws Exception {
		when(clientUseCases.search(any(), any())).thenThrow(
				new PropertyReferenceException("unknown", TypeInformation.of(Client.class), List.of()));

		mockMvc.perform(get("/api/clients").param("sort", "unknown"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Tri impossible sur la propriété inconnue : unknown"));
	}

	@Test
	void getsAClient() throws Exception {
		when(clientUseCases.get(1L)).thenReturn(client(ClientStatus.ACTIVE));

		mockMvc.perform(get("/api/clients/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lastName").value("Koné"));
	}

	@Test
	void reportsAnUnknownClient() throws Exception {
		when(clientUseCases.get(99L)).thenThrow(new ResourceNotFoundException("Client", 99L));

		mockMvc.perform(get("/api/clients/99"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Ressource introuvable"))
				.andExpect(jsonPath("$.detail").value("Client introuvable (id 99)"))
				.andExpect(jsonPath("$.instance").value("/api/clients/99"));
	}

	@Test
	void rejectsANonNumericId() throws Exception {
		mockMvc.perform(get("/api/clients/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valeur invalide pour le paramètre id : abc"));
	}

	@Test
	void updatesAClient() throws Exception {
		when(clientUseCases.update(eq(1L), any())).thenReturn(client(ClientStatus.INACTIVE));

		mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(clientUseCases).update(1L,
				new UpdateClientCommand("Aya", "Koné", "0501020304", "Bouaké", ClientStatus.INACTIVE));
	}

	@Test
	void refusesToChangeTheEmail() throws Exception {
		String withEmail = VALID_UPDATE.replace("\"city\"", "\"email\": \"new@mail.ci\", \"city\"");

		mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(withEmail))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("email"))
				.andExpect(jsonPath("$.errors[0].message").value("n'est pas modifiable après la création du client"));

		verifyNoInteractions(clientUseCases);
	}

	@Test
	void rejectsAnUnknownStatusInTheBody() throws Exception {
		mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_UPDATE.replace("INACTIVE", "DELETED")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Valeur invalide pour le champ status : DELETED"));
	}

	@Test
	void reportsAConcurrentModification() throws Exception {
		when(clientUseCases.update(eq(1L), any())).thenThrow(new OptimisticLockingFailureException("stale"));

		mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Modification concurrente"));
	}

	@Test
	void deletesAClient() throws Exception {
		mockMvc.perform(delete("/api/clients/1")).andExpect(status().isNoContent());

		verify(clientUseCases).delete(1L);
	}

	@Test
	void refusesToDeleteAClientWithActiveSubscriptions() throws Exception {
		doThrow(new BusinessRuleViolationException("souscriptions en cours")).when(clientUseCases).delete(1L);

		mockMvc.perform(delete("/api/clients/1")).andExpect(status().isConflict());
	}

	@Test
	void summarizesAClient() throws Exception {
		Map<SubscriptionStatus, Long> counts = new LinkedHashMap<>();
		counts.put(SubscriptionStatus.PENDING, 0L);
		counts.put(SubscriptionStatus.ACTIVE, 2L);
		counts.put(SubscriptionStatus.SUSPENDED, 0L);
		counts.put(SubscriptionStatus.TERMINATED, 1L);
		when(clientUseCases.summarize(1L)).thenReturn(new ClientSummary(1L, counts, new BigDecimal("37000.00")));

		mockMvc.perform(get("/api/clients/1/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value(1))
				.andExpect(jsonPath("$.subscriptionsByStatus.ACTIVE").value(2))
				.andExpect(jsonPath("$.subscriptionsByStatus.PENDING").value(0))
				.andExpect(jsonPath("$.totalMonthlyRevenue").value(37000.00));
	}

	@Test
	void reportsAnUntranslatedIntegrityViolationAsAConflict() throws Exception {
		when(clientUseCases.get(1L)).thenThrow(new DataIntegrityViolationException("fk violation"));

		mockMvc.perform(get("/api/clients/1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Conflit de données"));
	}

	@Test
	void hidesTheDetailsOfUnexpectedErrors() throws Exception {
		when(clientUseCases.get(1L)).thenThrow(new IllegalStateException("password=secret"));

		mockMvc.perform(get("/api/clients/1"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Une erreur inattendue est survenue"))
				.andExpect(content().string(not(containsString("secret"))));
	}

	private static Client client(ClientStatus status) {
		Instant createdAt = Instant.parse("2026-10-14T10:00:00Z");
		return Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", status,
				new AuditInfo(createdAt, "conseiller", createdAt, "conseiller"));
	}

}
