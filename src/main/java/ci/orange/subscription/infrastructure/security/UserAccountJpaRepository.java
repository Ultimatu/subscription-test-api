package ci.orange.subscription.infrastructure.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, Long> {

	Optional<UserAccountEntity> findByUsername(String username);

}
