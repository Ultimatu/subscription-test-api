package ci.orange.subscription.infrastructure.persistence.subscription;

import ci.orange.subscription.infrastructure.persistence.entity.SubscriptionStatusHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubscriptionStatusHistoryJpaRepository extends JpaRepository<SubscriptionStatusHistoryEntity, Long> {

	List<SubscriptionStatusHistoryEntity> findBySubscriptionIdOrderByIdAsc(Long subscriptionId);

}
