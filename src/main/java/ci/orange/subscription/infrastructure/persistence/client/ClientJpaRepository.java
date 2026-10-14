package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ClientJpaRepository extends JpaRepository<ClientEntity, Long>, JpaSpecificationExecutor<ClientEntity> {

	boolean existsByEmailIgnoreCase(String email);

}
