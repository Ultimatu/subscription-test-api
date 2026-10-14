package ci.orange.subscription.infrastructure.web.subscription;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionDetails;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionUseCases;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.ClientOverview;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView.OfferOverview;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.InvalidStatusTransitionException;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import ci.orange.subscription.infrastructure.web.WebAdapterTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebAdapterTest({ SubscriptionController.class, ClientSubscriptionController.class })
class SubscriptionControllerTest {

	private static final LocalDate START = LocalDate.of(2026, 10, 20);

	private static final String VALID_CREATION = """
			{ "clientId": 1, "offerId": 2, "startDate": "2026-10-20" }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SubscriptionUseCases subscriptionUseCases;

	@Test
	void createsASubscriptionAndReturnsItsDetails() throws Exception {
		when(subscriptionUseCases.create(any())).thenReturn(Subscription.restore(10L, "SUB-2026-000001",
				"2700000001", 1L, 2L, SubscriptionStatus.PENDING, START, null, null));
		when(subscriptionUseCases.get(10L)).thenReturn(details(SubscriptionStatus.PENDING));

		mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATION))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/subscriptions/10")))
				.andExpect(jsonPath("$.reference").value("SUB-2026-000001"))
				.andExpect(jsonPath("$.nd").value("2700000001"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.startDate").value("2026-10-20"))
				.andExpect(jsonPath("$.client.lastName").value("Koné"))
				.andExpect(jsonPath("$.offer.code").value("FTTH-100"))
				.andExpect(jsonPath("$.history[0].to").value("PENDING"))
				.andExpect(jsonPath("$.subscription").doesNotExist());

		verify(subscriptionUseCases).create(new CreateSubscriptionCommand(1L, 2L, START));
	}

	@Test
	void refusesAnNdOrAReferenceChosenByTheCaller() throws Exception {
		mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"clientId\": 1, \"offerId\": 2, \"startDate\": \"2026-10-20\", \"nd\": \"2712345678\", "
						+ "\"reference\": \"SUB-2026-999999\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("nd", "reference")));

		verifyNoInteractions(subscriptionUseCases);
	}

	@Test
	void requiresTheClientTheOfferAndTheStartDate() throws Exception {
		mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(3));
	}

	@Test
	void reportsAStartDateInThePast() throws Exception {
		when(subscriptionUseCases.create(any())).thenThrow(
				new DomainValidationException(DomainErrorCode.START_DATE_IN_PAST, "date de début dans le passé"));

		mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATION))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("START_DATE_IN_PAST"));
	}

	@Test
	void reportsABrokenEligibilityRuleAsAConflict() throws Exception {
		when(subscriptionUseCases.create(any())).thenThrow(new BusinessRuleViolationException(
				DomainErrorCode.CLIENT_HAS_SUSPENDED_SUBSCRIPTION, "souscription suspendue"));

		mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATION))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CLIENT_HAS_SUSPENDED_SUBSCRIPTION"));
	}

	@Test
	void getsASubscriptionWithItsHistory() throws Exception {
		when(subscriptionUseCases.get(10L)).thenReturn(details(SubscriptionStatus.ACTIVE));

		mockMvc.perform(get("/api/subscriptions/10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.history[0].changedBy").value("conseiller"));
	}

	@Test
	void findsASubscriptionByItsNd() throws Exception {
		when(subscriptionUseCases.getByNd("2700000001")).thenReturn(details(SubscriptionStatus.ACTIVE));

		mockMvc.perform(get("/api/subscriptions/by-nd/2700000001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nd").value("2700000001"));
	}

	@Test
	void reportsAnUnknownNd() throws Exception {
		when(subscriptionUseCases.getByNd("2799999999")).thenThrow(new ResourceNotFoundException(
				DomainErrorCode.SUBSCRIPTION_NOT_FOUND, "Souscription", "ND", "2799999999"));

		mockMvc.perform(get("/api/subscriptions/by-nd/2799999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Souscription introuvable (ND 2799999999)"));
	}

	@Test
	void searchesWithEveryFilter() throws Exception {
		when(subscriptionUseCases.search(any(), any()))
				.thenReturn(new PageImpl<>(List.of(view(SubscriptionStatus.ACTIVE)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/subscriptions").param("status", "ACTIVE").param("technology", "FTTH")
				.param("city", "Abidjan").param("nd", "2700000001").param("startDateFrom", "2026-10-01")
				.param("startDateTo", "2026-10-31").param("sort", "startDate,desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].reference").value("SUB-2026-000001"))
				.andExpect(jsonPath("$.content[0].history").doesNotExist());

		verify(subscriptionUseCases).search(
				new SubscriptionSearchCriteria(SubscriptionStatus.ACTIVE, "FTTH", "Abidjan", "2700000001",
						LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startDate")));
	}

	@Test
	void rejectsADateThatIsNotIso() throws Exception {
		mockMvc.perform(get("/api/subscriptions").param("startDateFrom", "20/10/2026"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void activatesWithTheIdInTheBody() throws Exception {
		when(subscriptionUseCases.get(10L)).thenReturn(details(SubscriptionStatus.ACTIVE));

		mockMvc.perform(post("/api/subscriptions/activate").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subscriptionId\": 10 }"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		verify(subscriptionUseCases).activate(10L);
	}

	@Test
	void suspendsWithAReason() throws Exception {
		when(subscriptionUseCases.get(10L)).thenReturn(details(SubscriptionStatus.SUSPENDED));

		mockMvc.perform(post("/api/subscriptions/suspend").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subscriptionId\": 10, \"reason\": \"Impayé\" }"))
				.andExpect(status().isOk());

		verify(subscriptionUseCases).suspend(10L, "Impayé");
	}

	@Test
	void requiresAReasonToSuspendOrTerminate() throws Exception {
		for (String operation : List.of("suspend", "terminate")) {
			mockMvc.perform(post("/api/subscriptions/" + operation).contentType(MediaType.APPLICATION_JSON)
					.content("{ \"subscriptionId\": 10, \"reason\": \" \" }"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("reason"));
		}
		verifyNoInteractions(subscriptionUseCases);
	}

	@Test
	void reactivatesAndTerminates() throws Exception {
		when(subscriptionUseCases.get(10L)).thenReturn(details(SubscriptionStatus.TERMINATED));

		mockMvc.perform(post("/api/subscriptions/reactivate").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subscriptionId\": 10 }"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/subscriptions/terminate").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subscriptionId\": 10, \"reason\": \"Déménagement\" }"))
				.andExpect(status().isOk());

		verify(subscriptionUseCases).reactivate(10L);
		verify(subscriptionUseCases).terminate(10L, "Déménagement");
	}

	@Test
	void refusesAnNdInATransitionBody() throws Exception {
		mockMvc.perform(post("/api/subscriptions/activate").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subscriptionId\": 10, \"nd\": \"2712345678\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("nd"));

		verifyNoInteractions(subscriptionUseCases);
	}

	@Test
	void reportsAForbiddenTransitionWithTheCurrentAndRequiredStatuses() throws Exception {
		when(subscriptionUseCases.activate(10L)).thenThrow(
				new InvalidStatusTransitionException(SubscriptionTransition.ACTIVATE, SubscriptionStatus.ACTIVE));

		mockMvc.perform(post("/api/subscriptions/activate").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"subscriptionId\": 10 }"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
				.andExpect(jsonPath("$.currentStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.requiredStatuses", contains("PENDING")));
	}

	@Test
	void deletesAPendingSubscription() throws Exception {
		mockMvc.perform(delete("/api/subscriptions/10")).andExpect(status().isNoContent());

		verify(subscriptionUseCases).delete(10L);
	}

	@Test
	void refusesToDeleteASubscriptionThatWasActivated() throws Exception {
		doThrow(new BusinessRuleViolationException(DomainErrorCode.SUBSCRIPTION_NOT_DELETABLE, "à résilier"))
				.when(subscriptionUseCases).delete(10L);

		mockMvc.perform(delete("/api/subscriptions/10"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_DELETABLE"));
	}

	@Test
	void listsTheSubscriptionsOfAClient() throws Exception {
		when(subscriptionUseCases.listByClient(eq(1L), any()))
				.thenReturn(new PageImpl<>(List.of(view(SubscriptionStatus.ACTIVE)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/clients/1/subscriptions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].client.id").value(1))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void reportsAnUnknownClientWhenListingItsSubscriptions() throws Exception {
		when(subscriptionUseCases.listByClient(eq(99L), any()))
				.thenThrow(new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", 99L));

		mockMvc.perform(get("/api/clients/99/subscriptions"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
	}

	private static SubscriptionView view(SubscriptionStatus status) {
		return new SubscriptionView(10L, "SUB-2026-000001", "2700000001", status, "FTTH", START, null,
				new ClientOverview(1L, "Awa", "Koné", "Abidjan", ClientStatus.ACTIVE),
				new OfferOverview(2L, "FTTH-100", "Fibre Confort 100 Mbps", new BigDecimal("25000.00")), null);
	}

	private static SubscriptionDetails details(SubscriptionStatus status) {
		return new SubscriptionDetails(view(status), List.of(new StatusHistoryEntry(null, SubscriptionStatus.PENDING,
				null, Instant.parse("2026-10-14T10:00:00Z"), "conseiller")));
	}

}
