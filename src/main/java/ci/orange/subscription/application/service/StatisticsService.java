package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.stats.CityStatistics;
import ci.orange.subscription.application.port.in.stats.PeriodBasis;
import ci.orange.subscription.application.port.in.stats.StatisticsUseCases;
import ci.orange.subscription.application.port.in.stats.StatusStatistics;
import ci.orange.subscription.application.port.in.stats.TechnologyStatistics;
import ci.orange.subscription.application.port.out.StatisticsQueries;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.technology.Technology;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatisticsService implements StatisticsUseCases {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final StatisticsQueries statisticsQueries;
	private final TechnologyRepository technologyRepository;
	private final Clock clock;

	public StatisticsService(StatisticsQueries statisticsQueries, TechnologyRepository technologyRepository,
			Clock clock) {
		this.statisticsQueries = statisticsQueries;
		this.technologyRepository = technologyRepository;
		this.clock = clock;
	}

	@Override
	public List<CityStatistics> activeSubscriptionsByCity() {
		return statisticsQueries.activeSubscriptionsByCity().stream()
				.map(city -> new CityStatistics(city.city(), city.activeSubscriptions(),
						city.monthlyRevenue().setScale(2, RoundingMode.UNNECESSARY)))
				.toList();
	}

	/** Les parts sont arrondies à deux décimales : leur somme peut donc différer de 100 de quelques centièmes. */
	@Override
	public List<TechnologyStatistics> activeSubscriptionsByTechnology() {
		Map<String, Long> counts = statisticsQueries.activeSubscriptionsByTechnology();
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		return technologyRepository.findAll().stream()
				.map(technology -> statistics(technology, counts.getOrDefault(technology.getCode(), 0L), total))
				.sorted(Comparator.comparingLong(TechnologyStatistics::activeSubscriptions).reversed()
						.thenComparing(TechnologyStatistics::technology))
				.toList();
	}

	@Override
	public StatusStatistics subscriptionsByStatus(LocalDate from, LocalDate to, PeriodBasis basedOn) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new DomainValidationException(DomainErrorCode.INVALID_DATE_RANGE,
					"from (%s) doit être antérieure ou égale à to (%s)".formatted(from, to));
		}
		PeriodBasis basis = basedOn != null ? basedOn : PeriodBasis.CREATION_DATE;
		Instant start = from != null ? from.atStartOfDay(clock.getZone()).toInstant() : null;
		Instant end = to != null ? to.plusDays(1).atStartOfDay(clock.getZone()).toInstant() : null;

		Map<SubscriptionStatus, Long> counts = switch (basis) {
			case CREATION_DATE -> statisticsQueries.countByCurrentStatusCreatedBetween(start, end);
			case START_DATE -> statisticsQueries.countByCurrentStatusStartingBetween(from, to);
			case STATUS_CHANGE -> statisticsQueries.countByStatusEnteredBetween(start, end);
		};
		long total = basis == PeriodBasis.STATUS_CHANGE ? statisticsQueries.countChangedBetween(start, end)
				: counts.values().stream().mapToLong(Long::longValue).sum();
		return new StatusStatistics(from, to, basis, withEveryStatus(counts), total);
	}

	private static TechnologyStatistics statistics(Technology technology, long count, long total) {
		BigDecimal share = total == 0 ? BigDecimal.ZERO.setScale(2)
				: BigDecimal.valueOf(count).multiply(HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
		return new TechnologyStatistics(technology.getCode(), technology.getLabel(), count, share);
	}

	private static Map<SubscriptionStatus, Long> withEveryStatus(Map<SubscriptionStatus, Long> counts) {
		Map<SubscriptionStatus, Long> all = new EnumMap<>(SubscriptionStatus.class);
		for (SubscriptionStatus status : SubscriptionStatus.values()) {
			all.put(status, counts.getOrDefault(status, 0L));
		}
		return all;
	}

}
