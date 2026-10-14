package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.infrastructure.persistence.subscription.SubscriptionJpaRepository.StatusCount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SubscriptionPersistenceAdapter implements SubscriptionRepository {

	private final SubscriptionJpaRepository jpaRepository;
	private final AuditorAware<String> auditorAware;
	private final Clock clock;

	@Override
	public boolean existsByClientIdAndStatusIn(Long clientId, Set<SubscriptionStatus> statuses) {
		return jpaRepository.existsByClientIdAndStatusIn(clientId, statuses);
	}

	@Override
	public Map<SubscriptionStatus, Long> countByStatusForClient(Long clientId) {
		Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
		for (StatusCount count : jpaRepository.countByStatusForClient(clientId)) {
			counts.put(count.getStatus(), count.getTotal());
		}
		return counts;
	}

	@Override
	public BigDecimal sumMonthlyPriceForClient(Long clientId, SubscriptionStatus status) {
		return jpaRepository.sumMonthlyPriceForClient(clientId, status);
	}

	@Override
	public void deleteAllByClientId(Long clientId) {
		Instant now = clock.instant();
		String auditor = auditorAware.getCurrentAuditor().orElseThrow();
		jpaRepository.findAllByClientId(clientId).forEach(entity -> entity.markDeleted(now, auditor));
		jpaRepository.flush();
	}

}
