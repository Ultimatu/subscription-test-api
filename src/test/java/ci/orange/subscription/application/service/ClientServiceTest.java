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
import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.client.DeletedClient;
import ci.orange.subscription.application.port.in.client.UpdateClientCommand;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

	@Mock
	private ClientRepository clientRepository;

	@Mock
	private SubscriptionRepository subscriptionRepository;

	private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
	private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

	/** La configuration de développement : suppression logique, purge permise, sans délai de conservation. */
	private static final DeletionPolicy DEVELOPMENT = new DeletionPolicy(true, true, false, Period.ofYears(3));
	private static final DeletionPolicy WITH_RETENTION = new DeletionPolicy(true, true, true, Period.ofYears(3));

	@Mock
	private AuditTrail auditTrail;

	private ClientService clientService;

	@BeforeEach
	void createService() {
		clientService = service(DEVELOPMENT);
	}

	private ClientService service(DeletionPolicy policy) {
		return new ClientService(clientRepository, subscriptionRepository, policy, auditTrail, Clock.fixed(NOW, ABIDJAN));
	}

	@Test
	void createsAnActiveClient() {
		when(clientRepository.existsByEmail("awa@mail.ci")).thenReturn(false);
		when(clientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Client client = clientService.create(
				new CreateClientCommand("Awa", "Koné", "Awa@Mail.ci", "0701020304", "Abidjan"));

		assertThat(client.getStatus()).isEqualTo(ClientStatus.ACTIVE);
		assertThat(client.getEmail()).isEqualTo("awa@mail.ci");
		verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_CREATED
				&& entry.summary().equals("Client Awa Koné créé")));
	}

	@Test
	void exportsTheMatchingClientsBatchByBatch() {
		ClientSearchCriteria criteria = new ClientSearchCriteria("Abidjan", null, null);
		Client awa = Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", ClientStatus.ACTIVE,
				null);
		when(clientRepository.findBatch(criteria, null, 500)).thenReturn(List.of(awa));
		List<Client> exported = new ArrayList<>();

		long count = clientService.export(criteria).forEachBatch(exported::addAll);

		assertThat(count).isEqualTo(1);
		assertThat(exported).containsExactly(awa);
		ArgumentCaptor<AuditTrail.Entry> entry = ArgumentCaptor.forClass(AuditTrail.Entry.class);
		verify(auditTrail).record(entry.capture());
		assertThat(entry.getValue().action()).isEqualTo(AuditAction.CLIENTS_EXPORTED);
		assertThat(entry.getValue().details()).isEqualTo(Map.of("rows", 1L, "filters", List.of("city")));
	}

	@Test
	void refusesADuplicateEmail() {
		when(clientRepository.existsByEmail("awa@mail.ci")).thenReturn(true);

		assertThatThrownBy(() -> clientService
				.create(new CreateClientCommand("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan")))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_EMAIL_ALREADY_USED)
				.hasMessageContaining("awa@mail.ci");
		verify(clientRepository, never()).save(any());
	}

	@Test
	void delegatesTheSearch() {
		ClientSearchCriteria criteria = new ClientSearchCriteria("Abidjan", ClientStatus.ACTIVE, "kon");
		Pageable pageable = PageRequest.of(0, 20);
		Page<Client> page = new PageImpl<>(List.of(existingClient()));
		when(clientRepository.search(criteria, pageable)).thenReturn(page);

		assertThat(clientService.search(criteria, pageable)).isSameAs(page);
	}

	@Test
	void getsAClient() {
		Client client = existingClient();
		when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

		assertThat(clientService.get(1L)).isSameAs(client);
	}

	@Test
	void reportsAnUnknownClient() {
		when(clientRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> clientService.get(99L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_NOT_FOUND)
				.hasMessageContaining("99");
	}

	@Test
	void updatesAClient() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(clientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Client client = clientService.update(1L,
				new UpdateClientCommand("Aya", "Traoré", "0501020304", "Bouaké", ClientStatus.INACTIVE));

		assertThat(client.getFirstName()).isEqualTo("Aya");
		assertThat(client.getStatus()).isEqualTo(ClientStatus.INACTIVE);
		assertThat(client.getEmail()).isEqualTo("awa@mail.ci");
		ArgumentCaptor<AuditTrail.Entry> entry = ArgumentCaptor.forClass(AuditTrail.Entry.class);
		verify(auditTrail).record(entry.capture());
		assertThat(entry.getValue().action()).isEqualTo(AuditAction.CLIENT_UPDATED);
		assertThat(entry.getValue().clientId()).isEqualTo(1L);
		assertThat(entry.getValue().details()).containsOnlyKeys("firstName", "lastName", "phoneNumberClient", "city",
				"status");
		assertThat(entry.getValue().details().get("status"))
				.isEqualTo(Map.of("before", ClientStatus.ACTIVE, "after", ClientStatus.INACTIVE));
	}

	@Test
	void recordsNothingWhenAnUpdateChangesNothing() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(clientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		clientService.update(1L, new UpdateClientCommand("Awa", "Koné", "0701020304", "Abidjan", ClientStatus.ACTIVE));

		verifyNoInteractions(auditTrail);
	}

	@Test
	@SuppressWarnings("unchecked")
	void refusesToDeleteAClientWithOngoingOrSuspendedSubscriptions() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(true);

		assertThatThrownBy(() -> clientService.delete(1L))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_HAS_ONGOING_SUBSCRIPTIONS);

		ArgumentCaptor<java.util.Set<SubscriptionStatus>> statuses = ArgumentCaptor.forClass(java.util.Set.class);
		verify(subscriptionRepository).existsByClientIdAndStatusIn(eq(1L), statuses.capture());
		assertThat(statuses.getValue()).isEqualTo(
				EnumSet.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED));
		verify(clientRepository, never()).delete(any());
		verify(subscriptionRepository, never()).deleteAllByClientId(any());
	}

	@Test
	void keepsAClientWithAHistoryOfSubscriptionsThroughALogicalDeletion() {
		Client client = existingClient();
		when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(false);
		when(subscriptionRepository.existsAnyForClient(1L)).thenReturn(true);

		clientService.delete(1L);

		InOrder order = inOrder(subscriptionRepository, clientRepository);
		order.verify(subscriptionRepository).deleteAllByClientId(1L);
		order.verify(clientRepository).delete(client);
		verify(clientRepository, never()).deletePermanently(any());
		verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_DELETED
				&& entry.clientId().equals(1L)));
		verify(auditTrail, never()).eraseClient(any());
	}

	@Test
	void deletesPermanentlyAClientWhoNeverSubscribed() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsAnyForClient(1L)).thenReturn(false);

		clientService.delete(1L);

		verify(clientRepository).deletePermanently(1L);
		verify(clientRepository, never()).delete(any());
		InOrder order = inOrder(auditTrail);
		order.verify(auditTrail).eraseClient(1L);
		order.verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_DELETED
				&& entry.clientId() == null && entry.summary().equals("Client n° 1 supprimé définitivement")));
	}

	@Test
	void deletesPermanentlyWhenLogicalDeletionIsDisabled() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));

		service(new DeletionPolicy(false, true, false, Period.ofYears(3))).delete(1L);

		verify(clientRepository).deletePermanently(1L);
		verify(clientRepository, never()).delete(any());
	}

	@Test
	void purgesADeletedClientAtOnceWithoutRetention() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.of(NOW.minusSeconds(60)));

		clientService.purge(1L);

		verify(clientRepository).deletePermanently(1L);
		verify(auditTrail).eraseClient(1L);
		verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.CLIENT_PURGED
				&& entry.clientId() == null && entry.actor() == null));
	}

	@Test
	void purgesAClientThatIsStillVisibleWithoutRetention() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.empty());
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));

		clientService.purge(1L);

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
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsAnyForClient(1L)).thenReturn(true);

		assertThatThrownBy(() -> service(WITH_RETENTION).purge(1L))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.RETENTION_PERIOD_NOT_ELAPSED);
		verify(clientRepository, never()).deletePermanently(any());
	}

	@Test
	void refusesToPurgeAClientWithOngoingSubscriptions() {
		when(clientRepository.findDeletionDate(1L)).thenReturn(Optional.empty());
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(true);

		assertThatThrownBy(() -> clientService.purge(1L))
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
		assertThat(clientService.listDeleted(PageRequest.of(0, 20)).getContent())
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
		assertThat(clientService.purgeExpired()).isZero();
		verifyNoInteractions(clientRepository);
	}

	@Test
	void summarizesEveryStatusAndTheActiveRevenue() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.countByStatusForClient(1L))
				.thenReturn(Map.of(SubscriptionStatus.ACTIVE, 2L, SubscriptionStatus.TERMINATED, 1L));
		when(subscriptionRepository.sumMonthlyPriceForClient(1L, SubscriptionStatus.ACTIVE))
				.thenReturn(new BigDecimal("37000.00"));

		ClientSummary summary = clientService.summarize(1L);

		assertThat(summary.clientId()).isEqualTo(1L);
		assertThat(summary.subscriptionsByStatus()).containsExactly(
				Map.entry(SubscriptionStatus.PENDING, 0L),
				Map.entry(SubscriptionStatus.ACTIVE, 2L),
				Map.entry(SubscriptionStatus.SUSPENDED, 0L),
				Map.entry(SubscriptionStatus.TERMINATED, 1L));
		assertThat(summary.totalMonthlyRevenue()).isEqualByComparingTo("37000");
	}

	@Test
	void expressesAZeroRevenueWithTwoDecimals() {
		when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient()));
		when(subscriptionRepository.countByStatusForClient(1L)).thenReturn(Map.of());
		when(subscriptionRepository.sumMonthlyPriceForClient(1L, SubscriptionStatus.ACTIVE)).thenReturn(BigDecimal.ZERO);

		assertThat(clientService.summarize(1L).totalMonthlyRevenue()).isEqualTo(new BigDecimal("0.00"));
	}

	@Test
	void doesNotSummarizeAnUnknownClient() {
		when(clientRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> clientService.summarize(99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	private static Client existingClient() {
		return Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", ClientStatus.ACTIVE, null);
	}

}
