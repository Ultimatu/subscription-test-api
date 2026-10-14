package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionNumberSequences;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.domain.subscription.StatusChange;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

	private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

	@Mock
	private SubscriptionRepository subscriptionRepository;

	@Mock
	private ClientRepository clientRepository;

	@Mock
	private OfferRepository offerRepository;

	@Mock
	private SubscriptionNumberSequences numberSequences;

	private SubscriptionService subscriptionService;

	@BeforeEach
	void createService() {
		Clock clock = Clock.fixed(TODAY.atStartOfDay(ABIDJAN).toInstant().plusSeconds(3600), ABIDJAN);
		subscriptionService = new SubscriptionService(subscriptionRepository, clientRepository, offerRepository,
				numberSequences, clock);
	}

	@Nested
	class Creation {

		@Test
		void createsAPendingSubscriptionWithServerAssignedNumbers() {
			givenClient(ClientStatus.ACTIVE);
			givenOffer(true);
			when(numberSequences.nextReference(2026)).thenReturn(7L);
			when(numberSequences.nextNd()).thenReturn(42L);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			Subscription subscription = subscriptionService.create(new CreateSubscriptionCommand(1L, 2L, TODAY));

			assertThat(subscription.getReference()).isEqualTo("SUB-2026-000007");
			assertThat(subscription.getNd()).isEqualTo("2700000042");
			assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
			assertThat(subscription.getClientId()).isEqualTo(1L);
			assertThat(subscription.getOfferId()).isEqualTo(2L);
			assertThat(subscription.recordedStatusChanges())
					.containsExactly(new StatusChange(null, SubscriptionStatus.PENDING, null));
		}

		@Test
		void locksTheClientAndChecksTheRulesAgainstItsSubscriptions() {
			givenClient(ClientStatus.ACTIVE);
			givenOffer(true);
			when(subscriptionRepository.existsByClientIdAndStatusIn(1L, EnumSet.of(SubscriptionStatus.SUSPENDED)))
					.thenReturn(false);
			when(subscriptionRepository.existsOngoingForClientAndTechnology(1L, "FTTH")).thenReturn(true);

			assertThatThrownBy(() -> subscriptionService.create(new CreateSubscriptionCommand(1L, 2L, TODAY)))
					.isInstanceOf(BusinessRuleViolationException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_ALREADY_SUBSCRIBED);

			verify(clientRepository).findByIdForUpdate(1L);
			verify(clientRepository, never()).findById(any());
		}

		@Test
		void drawsNoNumberWhenARuleIsBroken() {
			givenClient(ClientStatus.INACTIVE);
			givenOffer(true);

			assertThatThrownBy(() -> subscriptionService.create(new CreateSubscriptionCommand(1L, 2L, TODAY)))
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_INACTIVE);

			verifyNoInteractions(numberSequences);
			verify(subscriptionRepository, never()).save(any());
		}

		@Test
		void rejectsAPastStartDateBeforeAnythingElse() {
			assertThatThrownBy(() -> subscriptionService
					.create(new CreateSubscriptionCommand(99L, 99L, TODAY.minusDays(1))))
					.isInstanceOf(DomainValidationException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.START_DATE_IN_PAST);

			verifyNoInteractions(clientRepository, offerRepository, subscriptionRepository, numberSequences);
		}

		@Test
		void acceptsAFutureStartDate() {
			givenClient(ClientStatus.ACTIVE);
			givenOffer(true);
			when(numberSequences.nextReference(anyInt())).thenReturn(1L);
			when(numberSequences.nextNd()).thenReturn(1L);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			Subscription subscription = subscriptionService
					.create(new CreateSubscriptionCommand(1L, 2L, TODAY.plusMonths(1)));

			assertThat(subscription.getStartDate()).isEqualTo(TODAY.plusMonths(1));
		}

		@Test
		void reportsAnUnknownClient() {
			when(clientRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> subscriptionService.create(new CreateSubscriptionCommand(99L, 2L, TODAY)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_NOT_FOUND);
		}

		@Test
		void reportsAnUnknownOffer() {
			givenClient(ClientStatus.ACTIVE);
			when(offerRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> subscriptionService.create(new CreateSubscriptionCommand(1L, 99L, TODAY)))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.OFFER_NOT_FOUND);
		}

		@Test
		void usesTheYearOfTheCreationForTheReference() {
			givenClient(ClientStatus.ACTIVE);
			givenOffer(true);
			when(numberSequences.nextReference(anyInt())).thenReturn(1L);
			when(numberSequences.nextNd()).thenReturn(1L);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			subscriptionService.create(new CreateSubscriptionCommand(1L, 2L, LocalDate.of(2027, 2, 1)));

			ArgumentCaptor<Integer> year = ArgumentCaptor.forClass(Integer.class);
			verify(numberSequences).nextReference(year.capture());
			assertThat(year.getValue()).isEqualTo(2026);
		}

	}

	private void givenClient(ClientStatus status) {
		when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(
				Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", status, null)));
	}

	private void givenOffer(boolean active) {
		when(offerRepository.findById(2L)).thenReturn(Optional.of(
				Offer.restore(2L, "FTTH-100", "Fibre Confort 100 Mbps", "FTTH", new BigDecimal("25000.00"), active,
						null)));
	}

}
