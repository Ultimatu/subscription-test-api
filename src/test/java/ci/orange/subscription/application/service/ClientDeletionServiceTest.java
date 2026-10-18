package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.client.DeletedClient;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ClientDeletionServiceTest {

	private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
	private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

	/** La configuration de développement : suppression logique, purge permise, sans délai de conservation. */
	private static final DeletionPolicy DEVELOPMENT = new DeletionPolicy(true, true, false, Period.ofYears(3));
	private static final DeletionPolicy WITH_RETENTION = new DeletionPolicy(true, true, true, Period.ofYears(3));

	@Mock
	private ClientRepository clientRepository;

	@Mock
	private SubscriptionRepository subscriptionRepository;

	@Mock
	private AuditTrail auditTrail;

	private ClientDeletionService deletionService;

	@BeforeEach
	void createService() {
		deletionService = service(DEVELOPMENT);
	}

	private ClientDeletionService service(DeletionPolicy policy) {
		return new ClientDeletionService(clientRepository, subscriptionRepository, policy, auditTrail,
				Clock.fixed(NOW, ABIDJAN));
	}

	@Test
	@SuppressWarnings("unchecked")
	void refusesToDeleteAClientWithOngoingOrSuspendedSubscriptions() {
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(true);

		assertThatThrownBy(() -> deletionService.delete(1L))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_HAS_ONGOING_SUBSCRIPTIONS);

		ArgumentCaptor<Set<SubscriptionStatus>> statuses = ArgumentCaptor.forClass(Set.class);
		verify(subscriptionRepository).existsByClientIdAndStatusIn(eq(1L), statuses.capture());
		assertThat(statuses.getValue()).isEqualTo(
				EnumSet.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED));
		verify(clientRepository, never()).delete(any());
		verify(subscriptionRepository, never()).deleteAllByClientId(any());
	}

	@Test
	void keepsAClientWithAHistoryOfSubscriptionsThroughALogicalDeletion() {
		Client client = existingClient();
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(client));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(false);
		when(subscriptionRepository.existsAnyForClient(1L)).thenReturn(true);

		deletionService.delete(1L);

		InOrder order = inOrder(subscriptionRepository, clientRepository);
		order.verify(clientRepository).findByIdForUpdate(1L);
		order.verify(subscriptionRepository).deleteAllByClientId(1L);
		order.verify(clientRepository).delete(client);
		verify(clientRepository, never()).deletePermanently(any());
		verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_DELETED
				&& entry.clientId().equals(1L)
				&& entry.summary().equals("Client Awa Koné supprimé, conservé jusqu'à sa purge")));
		verify(auditTrail, never()).eraseClient(any());
	}

	@Test
	void deletesPermanentlyAClientWhoNeverSubscribed() {
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsAnyForClient(1L)).thenReturn(false);

		deletionService.delete(1L);

		verify(clientRepository).deletePermanently(1L);
		verify(clientRepository, never()).delete(any());
		InOrder order = inOrder(auditTrail);
		order.verify(auditTrail).eraseClient(1L);
		order.verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_DELETED
				&& entry.clientId() == null && entry.summary().equals("Client n° 1 supprimé définitivement")));
	}

	@Test
	void deletesPermanentlyWhenLogicalDeletionIsDisabled() {
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingClient()));

		service(new DeletionPolicy(false, true, false, Period.ofYears(3))).delete(1L);

		verify(clientRepository).deletePermanently(1L);
		verify(clientRepository, never()).delete(any());
	}

	@Test
	void purgesADeletedClientAtOnceWithoutRetention() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.of(NOW.minusSeconds(60)));

		deletionService.purge(1L);

		verify(clientRepository).deletePermanently(1L);
		verify(auditTrail).eraseClient(1L);
		verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_PURGED
				&& entry.clientId() == null && entry.actor() == null));
	}

	@Test
	void purgesAClientThatIsStillVisibleWithoutRetention() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.empty());
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingClient()));

		deletionService.purge(1L);

		verify(clientRepository).deletePermanently(1L);
	}

	@Test
	void refusesToPurgeBeforeTheEndOfTheRetentionPeriod() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.of(Instant.parse("2025-01-10T09:00:00Z")));

		assertThatThrownBy(() -> service(WITH_RETENTION).purge(1L))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.RETENTION_PERIOD_NOT_ELAPSED)
				.hasMessageContaining("10/01/2028");
		verify(clientRepository, never()).deletePermanently(any());
	}

	@Test
	void purgesOnceTheRetentionPeriodIsOver() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.of(Instant.parse("2023-10-15T09:00:00Z")));

		service(WITH_RETENTION).purge(1L);

		verify(clientRepository).deletePermanently(1L);
	}

	@Test
	void requiresALogicalDeletionFirstForAClientWithAHistoryUnderRetention() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.empty());
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsAnyForClient(1L)).thenReturn(true);

		assertThatThrownBy(() -> service(WITH_RETENTION).purge(1L))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.RETENTION_PERIOD_NOT_ELAPSED);
		verify(clientRepository, never()).deletePermanently(any());
	}

	@Test
	void refusesToPurgeAClientWithOngoingSubscriptions() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.empty());
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(true);

		assertThatThrownBy(() -> deletionService.purge(1L))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_HAS_ONGOING_SUBSCRIPTIONS);
	}

	@Test
	void refusesToPurgeWhenPermanentDeletionIsDisabled() {
		assertThatThrownBy(() -> service(new DeletionPolicy(true, false, false, Period.ofYears(3))).purge(1L))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.HARD_DELETE_DISABLED);
		verifyNoInteractions(clientRepository);
	}

	@Test
	void listsTheDeletedClientsWithTheDateTheyCanBePurged() {
		Instant deletedAt = Instant.parse("2026-10-01T08:00:00Z");
		when(clientRepository.findDeleted(any())).thenReturn(new PageImpl<>(List.of(new ClientRepository.Deleted(1L,
				"Awa", "Koné", "awa@mail.ci", deletedAt, "admin"))));

		assertThat(service(WITH_RETENTION).listDeleted(PageRequest.of(0, 20)).getContent())
				.extracting(DeletedClient::purgeableFrom)
				.containsExactly(Instant.parse("2029-10-01T08:00:00Z"));
		assertThat(deletionService.listDeleted(PageRequest.of(0, 20)).getContent())
				.extracting(DeletedClient::purgeableFrom)
				.containsExactly(deletedAt);
	}

	@Test
	void purgesAutomaticallyTheClientsWhoseRetentionPeriodIsOver() {
		when(clientRepository.findDeletedBefore(Instant.parse("2023-10-15T10:00:00Z"))).thenReturn(List.of(4L, 7L));

		assertThat(service(WITH_RETENTION).purgeExpired()).isEqualTo(2);

		verify(clientRepository).deletePermanently(4L);
		verify(clientRepository).deletePermanently(7L);
		verify(auditTrail, times(2)).record(argThat(entry -> entry.action() == AuditAction.CLIENT_PURGED
				&& "system".equals(entry.actor())));
	}

	@Test
	void doesNotPurgeAutomaticallyWithoutRetention() {
		assertThat(deletionService.purgeExpired()).isZero();
		verifyNoInteractions(clientRepository);
	}

	private static Client existingClient() {
		return Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", ClientStatus.ACTIVE, null);
	}

}
