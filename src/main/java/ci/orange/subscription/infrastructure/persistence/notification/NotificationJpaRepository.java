package ci.orange.subscription.infrastructure.persistence.notification;

import ci.orange.subscription.infrastructure.persistence.entity.NotificationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {

	/**
	 * Verrouille les notifications dues, en sautant celles qu'une autre instance a déjà verrouillées
	 * ({@code SKIP LOCKED}) : deux instances de l'application ne réservent jamais la même notification.
	 * Sert l'index partiel {@code ix_notification_due}.
	 */
	@Query(value = """
			select * from notification_outbox
			where status = 'PENDING' and next_attempt_at <= :now
			order by next_attempt_at, id
			limit :limit
			for update skip locked
			""", nativeQuery = true)
	List<NotificationEntity> lockDue(@Param("now") Instant now, @Param("limit") int limit);

	List<NotificationEntity> findBySubscriptionIdOrderByIdAsc(Long subscriptionId);

}
