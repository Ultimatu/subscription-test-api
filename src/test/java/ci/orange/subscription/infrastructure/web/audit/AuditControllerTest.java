package ci.orange.subscription.infrastructure.web.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditEvent;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.audit.AuditSearchCriteria;
import ci.orange.subscription.application.port.in.audit.AuditUseCases;
import ci.orange.subscription.infrastructure.web.WebAdapterTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebAdapterTest(AuditController.class)
class AuditControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuditUseCases auditUseCases;

	@Test
	void searchesTheJournalWithTheGivenFilters() throws Exception {
		when(auditUseCases.search(any(), any())).thenReturn(new PageImpl<>(List.of(new AuditEvent(1L,
				Instant.parse("2026-10-15T08:00:00Z"), "conseiller", AuditAction.SUBSCRIPTION_SUSPENDED,
				AuditResource.SUBSCRIPTION, "10", "Souscription SUB-2026-000001 suspendue", Map.of("reason", "Impayé"),
				"req-1"))));

		mockMvc.perform(get("/api/audit-events").param("resourceType", "SUBSCRIPTION").param("resourceId", "10")
				.param("actor", "conseiller").param("action", "SUBSCRIPTION_SUSPENDED").param("from", "2026-10-01")
				.param("to", "2026-10-15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].summary").value("Souscription SUB-2026-000001 suspendue"))
				.andExpect(jsonPath("$.content[0].details.reason").value("Impayé"))
				.andExpect(jsonPath("$.content[0].requestId").value("req-1"));

		verify(auditUseCases).search(eq(new AuditSearchCriteria(AuditResource.SUBSCRIPTION, "10", "conseiller",
				AuditAction.SUBSCRIPTION_SUSPENDED, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))), any());
	}

	@Test
	void refusesAnUnknownAction() throws Exception {
		mockMvc.perform(get("/api/audit-events").param("action", "SOMETHING"))
				.andExpect(status().isBadRequest());
	}

}
