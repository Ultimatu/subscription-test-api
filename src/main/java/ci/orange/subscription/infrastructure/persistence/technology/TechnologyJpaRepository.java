package ci.orange.subscription.infrastructure.persistence.technology;

import ci.orange.subscription.infrastructure.persistence.entity.TechnologyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TechnologyJpaRepository
		extends JpaRepository<TechnologyEntity, String>, JpaSpecificationExecutor<TechnologyEntity> {
}
