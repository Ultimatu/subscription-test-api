package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.client.ClientSearchCriteria;
import ci.orange.subscription.application.port.in.client.ClientSummary;
import ci.orange.subscription.application.port.in.client.CreateClientCommand;
import ci.orange.subscription.application.port.in.client.UpdateClientCommand;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
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

	@InjectMocks
	private ClientService clientService;

	@Test
	void createsAnActiveClient() {
		when(clientRepository.existsByEmail("awa@mail.ci")).thenReturn(false);
		when(clientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Client client = clientService.create(
				new CreateClientCommand("Awa", "Koné", "Awa@Mail.ci", "0701020304", "Abidjan"));

		assertThat(client.getStatus()).isEqualTo(ClientStatus.ACTIVE);
		assertThat(client.getEmail()).isEqualTo("awa@mail.ci");
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
	void deletesAClientWithItsTerminatedSubscriptions() {
		Client client = existingClient();
		when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
		when(subscriptionRepository.existsByClientIdAndStatusIn(eq(1L), anySet())).thenReturn(false);

		clientService.delete(1L);

		InOrder order = inOrder(subscriptionRepository, clientRepository);
		order.verify(subscriptionRepository).deleteAllByClientId(1L);
		order.verify(clientRepository).delete(client);
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
	void doesNotSummarizeAnUnknownClient() {
		when(clientRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> clientService.summarize(99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	private static Client existingClient() {
		return Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", ClientStatus.ACTIVE, null);
	}

}
