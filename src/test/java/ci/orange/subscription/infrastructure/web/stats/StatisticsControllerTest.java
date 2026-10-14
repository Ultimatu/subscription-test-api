package ci.orange.subscription.infrastructure.web.stats;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.application.port.in.stats.PeriodBasis;
import ci.orange.subscription.application.port.in.stats.StatisticsUseCases;
import ci.orange.subscription.application.port.in.stats.StatusStatistics;
import ci.orange.subscription.application.port.in.stats.TechnologyStatistics;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.web.WebAdapterTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebAdapterTest(StatisticsController.class)
class StatisticsControllerTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StatisticsUseCases statisticsUseCases;

	@Test
	void givesActiveSubscriptionsAndRevenueByCity() throws Exception {
		when(statisticsUseCases.activeSubscriptionsByCity())
				.thenReturn(List.of(new CityStatistics("Abidjan", 2, new BigDecimal("60000.00"))));

		mockMvc.perform(get("/api/stats/subscriptions-by-city"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].city").value("Abidjan"))
				.andExpect(jsonPath("$[0].activeSubscriptions").value(2))
				.andExpect(jsonPath("$[0].monthlyRevenue").value(60000.00));
	}

	@Test
	void givesTheDistributionByTechnology() throws Exception {
		when(statisticsUseCases.activeSubscriptionsByTechnology()).thenReturn(List.of(
				new TechnologyStatistics("FTTH", "Fibre optique", 2, new BigDecimal("66.67")),
				new TechnologyStatistics("4G_BOX", "Box 4G", 0, new BigDecimal("0.00"))));

		mockMvc.perform(get("/api/stats/subscriptions-by-technology"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].share").value(66.67))
				.andExpect(jsonPath("$[1].technology").value("4G_BOX"));
	}

	@Test
	void countsByStatusOverAPeriod() throws Exception {
		Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
		counts.put(SubscriptionStatus.PENDING, 1L);
		counts.put(SubscriptionStatus.ACTIVE, 3L);
		counts.put(SubscriptionStatus.SUSPENDED, 0L);
		counts.put(SubscriptionStatus.TERMINATED, 0L);
		when(statisticsUseCases.subscriptionsByStatus(FROM, TO, PeriodBasis.START_DATE))
				.thenReturn(new StatusStatistics(FROM, TO, PeriodBasis.START_DATE, counts, 4));

		mockMvc.perform(get("/api/stats/subscriptions-by-status").param("from", "2026-10-01").param("to", "2026-10-31")
				.param("basedOn", "START_DATE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.basedOn").value("START_DATE"))
				.andExpect(jsonPath("$.subscriptionsByStatus.ACTIVE").value(3))
				.andExpect(jsonPath("$.subscriptionsByStatus.SUSPENDED").value(0))
				.andExpect(jsonPath("$.total").value(4));
	}

	@Test
	void letsTheServiceChooseTheDefaultBasis() throws Exception {
		when(statisticsUseCases.subscriptionsByStatus(null, null, null)).thenReturn(
				new StatusStatistics(null, null, PeriodBasis.CREATION_DATE, Map.of(), 0));

		mockMvc.perform(get("/api/stats/subscriptions-by-status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.basedOn").value("CREATION_DATE"));

		verify(statisticsUseCases).subscriptionsByStatus(null, null, null);
	}

	@Test
	void rejectsAnUnknownBasis() throws Exception {
		mockMvc.perform(get("/api/stats/subscriptions-by-status").param("basedOn", "END_DATE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void reportsAnInvertedPeriod() throws Exception {
		when(statisticsUseCases.subscriptionsByStatus(TO, FROM, null))
				.thenThrow(new DomainValidationException(DomainErrorCode.INVALID_DATE_RANGE, "période inversée"));

		mockMvc.perform(get("/api/stats/subscriptions-by-status").param("from", "2026-10-31").param("to", "2026-10-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
	}

}
