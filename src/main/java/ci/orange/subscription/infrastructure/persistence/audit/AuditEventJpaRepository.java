package ci.orange.subscription.infrastructure.persistence.audit;

import ci.orange.subscription.infrastructure.persistence.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long>,
		JpaSpecificationExecutor<AuditEventEntity> {

	/** Sert l'index ix_audit_event_client. */
	@Modifying
	@Query("delete from AuditEventEntity event where event.clientId = :clientId")
	void deleteByClientId(@Param("clientId") Long clientId);

}
