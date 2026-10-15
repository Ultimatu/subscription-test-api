package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.application.port.in.stats.PeriodBasis;
import ci.orange.subscription.application.port.in.stats.StatusStatistics;
import ci.orange.subscription.application.port.in.stats.TechnologyStatistics;
import ci.orange.subscription.application.port.out.StatisticsQueries;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.technology.Technology;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	@Mock
	private StatisticsQueries statisticsQueries;

	@Mock
	private TechnologyRepository technologyRepository;

	private StatisticsService statisticsService;

	@BeforeEach
	void createService() {
		statisticsService = new StatisticsService(statisticsQueries, technologyRepository,
				Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneId.of("Africa/Abidjan")));
	}

	@Test
	void expressesTheRevenueOfEachCityWithTwoDecimals() {
		when(statisticsQueries.activeSubscriptionsByCity())
				.thenReturn(List.of(new CityStatistics("Abidjan", 2, new BigDecimal("40000"))));

		assertThat(statisticsService.activeSubscriptionsByCity())
				.containsExactly(new CityStatistics("Abidjan", 2, new BigDecimal("40000.00")));
	}

	@Test
	void listsEveryTechnologyOfTheCatalogueWithItsShare() {
		when(technologyRepository.findAll()).thenReturn(List.of(technology("4G_BOX"), technology("FTTH"),
				technology("SATELLITE")));
		when(statisticsQueries.activeSubscriptionsByTechnology()).thenReturn(Map.of("FTTH", 2L, "SATELLITE", 1L));

		assertThat(statisticsService.activeSubscriptionsByTechnology()).containsExactly(
				new TechnologyStatistics("FTTH", "Libellé FTTH", 2, new BigDecimal("66.67")),
				new TechnologyStatistics("SATELLITE", "Libellé SATELLITE", 1, new BigDecimal("33.33")),
				new TechnologyStatistics("4G_BOX", "Libellé 4G_BOX", 0, new BigDecimal("0.00")));
	}

	@Test
	void givesZeroSharesWhenNothingIsActive() {
		when(technologyRepository.findAll()).thenReturn(List.of(technology("FTTH")));
		when(statisticsQueries.activeSubscriptionsByTechnology()).thenReturn(Map.of());

		assertThat(statisticsService.activeSubscriptionsByTechnology())
				.containsExactly(new TechnologyStatistics("FTTH", "Libellé FTTH", 0, new BigDecimal("0.00")));
	}

	@Test
	void countsSubscriptionsCreatedInThePeriodByDefault() {
		when(statisticsQueries.countByCurrentStatusCreatedBetween(Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-01T00:00:00Z")))
				.thenReturn(Map.of(SubscriptionStatus.ACTIVE, 3L, SubscriptionStatus.TERMINATED, 1L));

		StatusStatistics statistics = statisticsService.subscriptionsByStatus(FROM, TO, null);

		assertThat(statistics.basedOn()).isEqualTo(PeriodBasis.CREATION_DATE);
		assertThat(statistics.subscriptionsByStatus()).containsExactly(
				Map.entry(SubscriptionStatus.PENDING, 0L),
				Map.entry(SubscriptionStatus.ACTIVE, 3L),
				Map.entry(SubscriptionStatus.SUSPENDED, 0L),
				Map.entry(SubscriptionStatus.TERMINATED, 1L));
		assertThat(statistics.total()).isEqualTo(4);
		assertThat(statistics.from()).isEqualTo(FROM);
		assertThat(statistics.to()).isEqualTo(TO);
	}

	@Test
	void countsSubscriptionsStartingInThePeriod() {
		when(statisticsQueries.countByCurrentStatusStartingBetween(FROM, TO))
				.thenReturn(Map.of(SubscriptionStatus.PENDING, 2L));

		StatusStatistics statistics = statisticsService.subscriptionsByStatus(FROM, TO, PeriodBasis.START_DATE);

		assertThat(statistics.subscriptionsByStatus()).containsEntry(SubscriptionStatus.PENDING, 2L);
		assertThat(statistics.total()).isEqualTo(2);
	}

	@Test
	void countsDistinctSubscriptionsForStatusChanges() {
		Instant start = Instant.parse("2026-10-01T00:00:00Z");
		Instant end = Instant.parse("2026-11-01T00:00:00Z");
		when(statisticsQueries.countByStatusEnteredBetween(start, end))
				.thenReturn(Map.of(SubscriptionStatus.ACTIVE, 2L, SubscriptionStatus.SUSPENDED, 1L));
		when(statisticsQueries.countChangedBetween(start, end)).thenReturn(2L);

		StatusStatistics statistics = statisticsService.subscriptionsByStatus(FROM, TO, PeriodBasis.STATUS_CHANGE);

		assertThat(statistics.subscriptionsByStatus()).containsEntry(SubscriptionStatus.SUSPENDED, 1L);
		assertThat(statistics.total()).isEqualTo(2);
	}

	@Test
	void acceptsAnOpenPeriod() {
		when(statisticsQueries.countByCurrentStatusCreatedBetween(null, null)).thenReturn(Map.of());

		assertThat(statisticsService.subscriptionsByStatus(null, null, null).total()).isZero();
		verify(statisticsQueries).countByCurrentStatusCreatedBetween(null, null);
	}

	@Test
	void refusesAnInvertedPeriod() {
		assertThatThrownBy(() -> statisticsService.subscriptionsByStatus(TO, FROM, null))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_DATE_RANGE);
		verifyNoInteractions(statisticsQueries);
	}

	@Test
	void convertsTheBoundsWithTheZoneOfTheApplicationClock() {
		StatisticsService inTokyo = new StatisticsService(statisticsQueries, technologyRepository,
				Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneId.of("Asia/Tokyo")));
		when(statisticsQueries.countByCurrentStatusCreatedBetween(any(), any())).thenReturn(Map.of());

		inTokyo.subscriptionsByStatus(FROM, FROM, null);

		// Minuit à Tokyo (UTC+9) : une journée locale entière, pas une journée UTC
		verify(statisticsQueries).countByCurrentStatusCreatedBetween(Instant.parse("2026-09-30T15:00:00Z"),
				Instant.parse("2026-10-01T15:00:00Z"));
	}

	private static Technology technology(String code) {
		return Technology.restore(code, "Libellé " + code, true, null);
	}

}
