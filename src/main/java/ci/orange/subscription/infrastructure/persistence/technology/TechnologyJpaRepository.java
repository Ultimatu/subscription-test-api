package ci.orange.subscription.infrastructure.persistence.technology;

import ci.orange.subscription.infrastructure.persistence.entity.TechnologyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface TechnologyJpaRepository extends JpaRepository<TechnologyEntity, String> {
}
