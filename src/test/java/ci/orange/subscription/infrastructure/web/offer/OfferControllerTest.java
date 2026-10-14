package ci.orange.subscription.infrastructure.web.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.offer.CreateOfferCommand;
import ci.orange.subscription.application.port.in.offer.OfferSearchCriteria;
import ci.orange.subscription.application.port.in.offer.OfferUseCases;
import ci.orange.subscription.application.port.in.offer.UpdateOfferCommand;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.infrastructure.web.WebAdapterTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebAdapterTest(OfferController.class)
class OfferControllerTest {

	private static final String VALID_OFFER = """
			{ "code": "FTTH-2000", "label": "Fibre Ultra 2 Gbps", "technology": "FTTH", "monthlyPrice": 200000 }
			""";

	private static final String VALID_UPDATE = """
			{ "label": "Fibre Confort Plus", "monthlyPrice": 27000.50 }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OfferUseCases offerUseCases;

	@Test
	void createsAnOffer() throws Exception {
		when(offerUseCases.create(any())).thenReturn(offer(true));

		mockMvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(VALID_OFFER))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/offers/1")))
				.andExpect(jsonPath("$.code").value("FTTH-2000"))
				.andExpect(jsonPath("$.technology").value("FTTH"))
				.andExpect(jsonPath("$.monthlyPrice").value(200000))
				.andExpect(jsonPath("$.active").value(true));

		verify(offerUseCases).create(
				new CreateOfferCommand("FTTH-2000", "Fibre Ultra 2 Gbps", "FTTH", new BigDecimal("200000")));
	}

	@Test
	void ignoresAnActiveFlagSentAtCreation() throws Exception {
		when(offerUseCases.create(any())).thenReturn(offer(true));

		mockMvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_OFFER.replace("\"code\"", "\"active\": false, \"code\"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.active").value(true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"code\": \"ftth-2000\"", "\"monthlyPrice\": 0", "\"monthlyPrice\": 15000.555" })
	void rejectsInvalidFields(String invalidField) throws Exception {
		String field = invalidField.substring(0, invalidField.indexOf(':'));
		String body = VALID_OFFER.replaceFirst(field + ": [^,}]+", invalidField);

		mockMvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value(field.replace("\"", "")));

		verifyNoInteractions(offerUseCases);
	}

	@Test
	void reportsAnUnknownTechnology() throws Exception {
		when(offerUseCases.create(any()))
				.thenThrow(new DomainValidationException(DomainErrorCode.UNKNOWN_TECHNOLOGY, "Technologie inconnue"));

		mockMvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(VALID_OFFER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNKNOWN_TECHNOLOGY"));
	}

	@Test
	void reportsADuplicateCode() throws Exception {
		when(offerUseCases.create(any()))
				.thenThrow(new BusinessRuleViolationException(DomainErrorCode.OFFER_CODE_ALREADY_USED, "doublon"));

		mockMvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(VALID_OFFER))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OFFER_CODE_ALREADY_USED"));
	}

	@Test
	void searchesByTechnologyAndActiveFlag() throws Exception {
		when(offerUseCases.search(any(), any()))
				.thenReturn(new PageImpl<>(List.of(offer(true)), PageRequest.of(0, 20), 1));

		mockMvc.perform(get("/api/offers").param("technology", "FTTH").param("active", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].code").value("FTTH-2000"))
				.andExpect(jsonPath("$.totalElements").value(1));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(offerUseCases).search(eq(new OfferSearchCriteria("FTTH", true)), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
	}

	@Test
	void rejectsAnInvalidActiveFilter() throws Exception {
		mockMvc.perform(get("/api/offers").param("active", "maybe"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void reportsAnUnknownOffer() throws Exception {
		when(offerUseCases.get(99L))
				.thenThrow(new ResourceNotFoundException(DomainErrorCode.OFFER_NOT_FOUND, "Offre", 99L));

		mockMvc.perform(get("/api/offers/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Offre introuvable (id 99)"));
	}

	@Test
	void updatesLabelAndPrice() throws Exception {
		when(offerUseCases.update(eq(1L), any())).thenReturn(offer(true));

		mockMvc.perform(put("/api/offers/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
				.andExpect(status().isOk());

		verify(offerUseCases).update(1L, new UpdateOfferCommand("Fibre Confort Plus", new BigDecimal("27000.50")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"code\": \"FTTH-1\"", "\"technology\": \"SATELLITE\"", "\"active\": false" })
	void refusesToChangeImmutableFieldsOrTheActiveFlag(String forbiddenField) throws Exception {
		String body = VALID_UPDATE.replace("\"label\"", forbiddenField + ", \"label\"");

		mockMvc.perform(put("/api/offers/1").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field")
						.value(forbiddenField.substring(1, forbiddenField.indexOf('"', 1))));

		verifyNoInteractions(offerUseCases);
	}

	@Test
	void deactivatesAnOffer() throws Exception {
		when(offerUseCases.deactivate(1L)).thenReturn(offer(false));

		mockMvc.perform(patch("/api/offers/1/deactivate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));
	}

	private static Offer offer(boolean active) {
		return Offer.restore(1L, "FTTH-2000", "Fibre Ultra 2 Gbps", "FTTH", new BigDecimal("200000"), active, null);
	}

}
