package ci.orange.subscription.infrastructure.persistence.client;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.infrastructure.persistence.entity.ClientEntity;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ClientPersistenceAdapter implements ClientRepository {

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_client_email";

	/**
	 * Ordre imposé par les clés étrangères. Les lignes supprimées logiquement sont invisibles pour JPA
	 * ({@code @SQLRestriction}) : la suppression définitive passe donc par du SQL natif.
	 */
	private static final List<String> PERMANENT_DELETION = List.of(
			"delete from notification_outbox where subscription_id in (select id from subscription where client_id = :id)",
			"delete from subscription_status_history where subscription_id in (select id from subscription where client_id = :id)",
			"delete from subscription where client_id = :id",
			"delete from client where id = :id");

	private final ClientJpaRepository jpaRepository;
	private final EntityManager entityManager;
	private final AuditorAware<String> auditorAware;
	private final Clock clock;

	ClientPersistenceAdapter(ClientJpaRepository jpaRepository, EntityManager entityManager,
			AuditorAware<String> auditorAware, Clock clock) {
		this.jpaRepository = jpaRepository;
		this.entityManager = entityManager;
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
		return jpaRepository.emailExists(email);
	}

	@Override
	public Page<Client> search(ClientSearchCriteria criteria, Pageable pageable) {
		return jpaRepository.findAll(ClientSpecifications.matching(criteria), pageable)
				.map(ClientEntityMapper::toDomain);
	}

	/** Tri sur la clé primaire et limite, sans requête de comptage. */
	@Override
	@Transactional(readOnly = true)
	public List<Client> findBatch(ClientSearchCriteria criteria, Long afterId, int size) {
		return jpaRepository
				.findBy(ClientSpecifications.matching(criteria).and(ClientSpecifications.after(afterId)),
						query -> query.sortBy(Sort.by("id")).limit(size).all())
				.stream()
				.map(ClientEntityMapper::toDomain)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Instant> findDeletionDate(Long id) {
		return entityManager
				.createNativeQuery("select deleted_at from client where id = :id and deleted_at is not null")
				.setParameter("id", id)
				.getResultList().stream()
				.findFirst()
				.map(ClientPersistenceAdapter::instant);
	}

	/** Du plus récent au plus ancien ; le tri demandé est ignoré. */
	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public Page<Deleted> findDeleted(Pageable pageable) {
		List<Object[]> rows = entityManager.createNativeQuery("""
				select id, first_name, last_name, email, deleted_at, deleted_by
				from client
				where deleted_at is not null
				order by deleted_at desc, id desc
				""")
				.setFirstResult((int) pageable.getOffset())
				.setMaxResults(pageable.getPageSize())
				.getResultList();
		long total = ((Number) entityManager
				.createNativeQuery("select count(*) from client where deleted_at is not null")
				.getSingleResult()).longValue();
		return new PageImpl<>(rows.stream()
				.map(row -> new Deleted(((Number) row[0]).longValue(), (String) row[1], (String) row[2],
						(String) row[3], instant(row[4]), (String) row[5]))
				.toList(), pageable, total);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Long> findDeletedBefore(Instant instant) {
		return entityManager
				.createNativeQuery("select id from client where deleted_at <= :instant order by id")
				.setParameter("instant", instant)
				.getResultList().stream()
				.map(id -> ((Number) id).longValue())
				.toList();
	}

	@Override
	@Transactional
	public void deletePermanently(Long id) {
		entityManager.flush();
		PERMANENT_DELETION.forEach(sql -> entityManager.createNativeQuery(sql).setParameter("id", id).executeUpdate());
		entityManager.clear();
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

	/** Selon le pilote, une colonne {@code timestamptz} lue en SQL natif arrive sous l'une de ces formes. */
	private static Instant instant(Object value) {
		if (value instanceof Instant instant) {
			return instant;
		}
		if (value instanceof OffsetDateTime dateTime) {
			return dateTime.toInstant();
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		throw new IllegalStateException("Date inattendue : " + value);
	}

}
