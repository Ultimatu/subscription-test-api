package ci.orange.subscription.infrastructure.persistence.offer;

import ci.orange.subscription.infrastructure.persistence.entity.OfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface OfferJpaRepository extends JpaRepository<OfferEntity, Long>, JpaSpecificationExecutor<OfferEntity> {

	boolean existsByCode(String code);

}
