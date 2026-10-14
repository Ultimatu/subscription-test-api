package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import java.time.Clock;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
class ClientPersistenceAdapter implements ClientRepository {

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_client_email";

	private final ClientJpaRepository jpaRepository;
	private final AuditorAware<String> auditorAware;
	private final Clock clock;

	ClientPersistenceAdapter(ClientJpaRepository jpaRepository, AuditorAware<String> auditorAware,
			Clock clock) {
		this.jpaRepository = jpaRepository;
		this.auditorAware = auditorAware;
		this.clock = clock;
	}

	@Override
	public Client save(Client client) {
		ClientEntity entity = client.getId() == null ? new ClientEntity() : findEntity(client.getId());
		ClientEntityMapper.copy(client, entity);
		try {
			return ClientEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
		}
		catch (DataIntegrityViolationException exception) {
			// Deux créations simultanées peuvent passer toutes les deux la vérification
			// existsByEmail : l'index unique tranche.
			if (violates(exception, EMAIL_UNIQUE_CONSTRAINT)) {
				throw new BusinessRuleViolationException(DomainErrorCode.CLIENT_EMAIL_ALREADY_USED,
						"Un client existe déjà avec l'email " + client.getEmail());
			}
			throw exception;
		}
	}

	@Override
	public Optional<Client> findById(Long id) {
		return jpaRepository.findById(id).map(ClientEntityMapper::toDomain);
	}

	@Override
	public Optional<Client> findByIdForUpdate(Long id) {
		return jpaRepository.findByIdForUpdate(id).map(ClientEntityMapper::toDomain);
	}

	@Override
	public boolean existsByEmail(String email) {
		return jpaRepository.existsByEmailIgnoreCase(email);
	}

	@Override
	public Page<Client> search(ClientSearchCriteria criteria, Pageable pageable) {
		return jpaRepository.findAll(ClientSpecifications.matching(criteria), pageable)
				.map(ClientEntityMapper::toDomain);
	}

	@Override
	public void delete(Client client) {
		ClientEntity entity = findEntity(client.getId());
		entity.markDeleted(clock.instant(), auditorAware.getCurrentAuditor().orElseThrow());
		jpaRepository.saveAndFlush(entity);
	}

	private ClientEntity findEntity(Long id) {
		return jpaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException(DomainErrorCode.CLIENT_NOT_FOUND, "Client", id));
	}

	private static boolean violates(DataIntegrityViolationException exception, String constraint) {
		return exception.getCause() instanceof ConstraintViolationException violation
				&& constraint.equals(violation.getConstraintName());
	}

}
