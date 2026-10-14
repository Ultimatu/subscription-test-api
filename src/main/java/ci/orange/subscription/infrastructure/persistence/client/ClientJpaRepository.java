package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClientJpaRepository extends JpaRepository<ClientEntity, Long>, JpaSpecificationExecutor<ClientEntity> {

	/**
	 * Écrite à la main pour comparer lower(email), l'expression de l'index unique partiel
	 * uk_client_email : la méthode dérivée existsByEmailIgnoreCase générerait upper(email), que
	 * l'index ne peut pas servir.
	 */
	@Query("""
			select case when count(c) > 0 then true else false end
			from ClientEntity c where lower(c.email) = lower(:email)
			""")
	boolean emailExists(@Param("email") String email);

	/** SELECT ... FOR UPDATE : la ligne reste verrouillée jusqu'à la fin de la transaction. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ClientEntity c where c.id = :id")
	Optional<ClientEntity> findByIdForUpdate(@Param("id") Long id);

}
