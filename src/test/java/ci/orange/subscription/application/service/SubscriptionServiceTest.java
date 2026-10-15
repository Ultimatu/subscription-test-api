package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ci.orange.subscription.application.port.in.audit.AuditAction;
import ci.orange.subscription.application.port.in.audit.AuditResource;
import ci.orange.subscription.application.port.in.subscription.CreateSubscriptionCommand;
import ci.orange.subscription.application.port.in.subscription.StatusHistoryEntry;
import ci.orange.subscription.application.port.in.subscription.SubscriptionDetails;
import ci.orange.subscription.application.port.in.subscription.SubscriptionSearchCriteria;
import ci.orange.subscription.application.port.in.subscription.SubscriptionView;
import ci.orange.subscription.application.port.out.AuditTrail;
import ci.orange.subscription.application.port.out.ClientRepository;
import ci.orange.subscription.application.port.out.NotificationOutbox;
import ci.orange.subscription.application.port.out.OfferRepository;
import ci.orange.subscription.application.port.out.SubscriptionNumberSequences;
import ci.orange.subscription.application.port.out.SubscriptionQueries;
import ci.orange.subscription.application.port.out.SubscriptionRepository;
import ci.orange.subscription.application.port.out.TechnologyRepository;
import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import ci.orange.subscription.domain.exception.ResourceNotFoundException;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.domain.subscription.StatusChange;
import ci.orange.subscription.domain.subscription.InvalidStatusTransitionException;
import ci.orange.subscription.domain.subscription.Subscription;
import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import ci.orange.subscription.domain.subscription.SubscriptionTransition;
import ci.orange.subscription.domain.technology.Technology;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
	private TechnologyRepository technologyRepository;

	@Mock
	private SubscriptionNumberSequences numberSequences;

	@Mock
	private SubscriptionQueries subscriptionQueries;

	@Mock
	private NotificationOutbox notificationOutbox;

	@Mock
	private AuditTrail auditTrail;

	private SubscriptionService subscriptionService;

	@BeforeEach
	void createService() {
		Clock clock = Clock.fixed(TODAY.atStartOfDay(ABIDJAN).toInstant().plusSeconds(3600), ABIDJAN);
		subscriptionService = new SubscriptionService(subscriptionRepository, subscriptionQueries, clientRepository,
				offerRepository, technologyRepository, numberSequences, notificationOutbox,
				new DeletionPolicy(true, true, false, Period.ofYears(3)), auditTrail, clock);
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
			verify(auditTrail).record(argThat(entry -> entry.action() == AuditAction.SUBSCRIPTION_CREATED
					&& entry.clientId().equals(1L) && entry.details().get("nd").equals("2700000042")));
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
		void refusesAnOfferWhoseTechnologyIsRetired() {
			givenClient(ClientStatus.ACTIVE);
			givenOffer(true);
			when(technologyRepository.findByCode("FTTH"))
					.thenReturn(Optional.of(Technology.restore("FTTH", "Fibre optique", false, null)));

			assertThatThrownBy(() -> subscriptionService.create(new CreateSubscriptionCommand(1L, 2L, TODAY)))
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_INACTIVE);
			verifyNoInteractions(numberSequences);
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

	@Nested
	class Transitions {

		@Test
		void activatesAPendingSubscriptionUnderTheClientLock() {
			givenSubscription(SubscriptionStatus.PENDING);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			Subscription subscription = subscriptionService.activate(10L);

			assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
			verify(clientRepository).findByIdForUpdate(1L);
			verify(notificationOutbox).enqueue(
					new NotificationOutbox.Request(10L, SubscriptionTransition.ACTIVATE, "awa@mail.ci", null));
		}

		@Test
		void suspendsWithItsReason() {
			givenSubscription(SubscriptionStatus.ACTIVE);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			Subscription subscription = subscriptionService.suspend(10L, "Impayé");

			assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
			assertThat(subscription.recordedStatusChanges())
					.containsExactly(new StatusChange(SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED, "Impayé"));
			verify(notificationOutbox).enqueue(
					new NotificationOutbox.Request(10L, SubscriptionTransition.SUSPEND, "awa@mail.ci", "Impayé"));
			verify(auditTrail).record(new AuditTrail.Entry(AuditAction.SUBSCRIPTION_SUSPENDED,
					AuditResource.SUBSCRIPTION, 10L, 1L, "Souscription SUB-2026-000001 suspendue",
					Map.of("reason", "Impayé")));
		}

		@Test
		void reactivatesASuspendedSubscription() {
			givenSubscription(SubscriptionStatus.SUSPENDED);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			assertThat(subscriptionService.reactivate(10L).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		}

		@Test
		void terminatesTodayInAbidjan() {
			givenSubscription(SubscriptionStatus.ACTIVE);
			when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

			Subscription subscription = subscriptionService.terminate(10L, "Déménagement");

			assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TERMINATED);
			assertThat(subscription.getEndDate()).isEqualTo(TODAY);
		}

		@Test
		void refusesATransitionOutsideOfTheSchemaWithoutSavingAnything() {
			givenSubscription(SubscriptionStatus.TERMINATED);

			assertThatThrownBy(() -> subscriptionService.reactivate(10L))
					.isInstanceOf(InvalidStatusTransitionException.class);
			verify(subscriptionRepository, never()).save(any());
			verifyNoInteractions(notificationOutbox);
		}

		@Test
		void reportsAnUnknownSubscription() {
			when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> subscriptionService.activate(99L))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.SUBSCRIPTION_NOT_FOUND);
		}

		@Test
		void deletesAPendingSubscription() {
			Subscription pending = givenSubscription(SubscriptionStatus.PENDING);

			subscriptionService.delete(10L);

			verify(subscriptionRepository).delete(pending);
			verify(subscriptionRepository, never()).deletePermanently(any());
		}

		@Test
		void deletesPermanentlyWhenLogicalDeletionIsDisabled() {
			Subscription pending = givenSubscription(SubscriptionStatus.PENDING);
			SubscriptionService hardDeleting = new SubscriptionService(subscriptionRepository, subscriptionQueries,
					clientRepository, offerRepository, technologyRepository, numberSequences, notificationOutbox,
					new DeletionPolicy(false, true, false, Period.ofYears(3)), auditTrail,
					Clock.fixed(TODAY.atStartOfDay(ABIDJAN).toInstant(), ABIDJAN));

			hardDeleting.delete(10L);

			verify(subscriptionRepository).deletePermanently(pending);
			verify(subscriptionRepository, never()).delete(any());
		}

		@Test
		void refusesToDeleteASubscriptionThatWasActivated() {
			givenSubscription(SubscriptionStatus.ACTIVE);

			assertThatThrownBy(() -> subscriptionService.delete(10L))
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.SUBSCRIPTION_NOT_DELETABLE);
			verify(subscriptionRepository, never()).delete(any());
		}

		private Subscription givenSubscription(SubscriptionStatus status) {
			Subscription subscription = Subscription.restore(10L, "SUB-2026-000001", "2700000001", 1L, 2L,
					new BigDecimal("25000.00"), status, TODAY, null, null);
			when(subscriptionRepository.findById(10L)).thenReturn(Optional.of(subscription));
			// Le client n'est relu, et verrouillé, que pour un changement de statut
			lenient().when(clientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(Client.restore(1L, "Awa",
					"Koné", "awa@mail.ci", "0701020304", "Abidjan", ClientStatus.ACTIVE, null)));
			return subscription;
		}

	}

	@Nested
	class Reads {

		private final SubscriptionView view = new SubscriptionView(10L, "SUB-2026-000001", "2700000001",
				SubscriptionStatus.ACTIVE, "FTTH", new BigDecimal("25000.00"), TODAY, null, null, null, null);

		@Test
		void getsASubscriptionWithItsHistory() {
			List<StatusHistoryEntry> history = List
					.of(new StatusHistoryEntry(null, SubscriptionStatus.PENDING, null, null, "conseiller"));
			when(subscriptionQueries.findById(10L)).thenReturn(Optional.of(view));
			when(subscriptionQueries.findHistory(10L)).thenReturn(history);

			assertThat(subscriptionService.get(10L)).isEqualTo(new SubscriptionDetails(view, history));
		}

		@Test
		void findsASubscriptionByItsNd() {
			when(subscriptionQueries.findByNd("2700000001")).thenReturn(Optional.of(view));
			when(subscriptionQueries.findHistory(10L)).thenReturn(List.of());

			assertThat(subscriptionService.getByNd("2700000001").subscription()).isEqualTo(view);
		}

		@Test
		void reportsAnUnknownNdWithTheNdInTheMessage() {
			when(subscriptionQueries.findByNd("2799999999")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> subscriptionService.getByNd("2799999999"))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.SUBSCRIPTION_NOT_FOUND)
					.hasMessage("Souscription introuvable (ND 2799999999)");
		}

		@Test
		void searchesWithTheCriteria() {
			SubscriptionSearchCriteria criteria = new SubscriptionSearchCriteria(SubscriptionStatus.ACTIVE, "FTTH",
					"Abidjan", null, TODAY.minusDays(30), TODAY);
			Page<SubscriptionView> page = new PageImpl<>(List.of(view));
			when(subscriptionQueries.search(criteria, PageRequest.of(0, 20))).thenReturn(page);

			assertThat(subscriptionService.search(criteria, PageRequest.of(0, 20))).isSameAs(page);
		}

		@Test
		void refusesAnInvertedDateRange() {
			SubscriptionSearchCriteria criteria = new SubscriptionSearchCriteria(null, null, null, null, TODAY,
					TODAY.minusDays(1));

			assertThatThrownBy(() -> subscriptionService.search(criteria, PageRequest.of(0, 20)))
					.isInstanceOf(DomainValidationException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_DATE_RANGE);
			verifyNoInteractions(subscriptionQueries);
		}

		@Test
		void exportsTheMatchingSubscriptionsBatchByBatch() {
			SubscriptionSearchCriteria criteria = new SubscriptionSearchCriteria(SubscriptionStatus.ACTIVE, null, null,
					null, null, null);
			when(subscriptionQueries.findBatch(criteria, null, 500)).thenReturn(List.of(view));
			List<SubscriptionView> exported = new ArrayList<>();

			long count = subscriptionService.export(criteria).forEachBatch(exported::addAll);

			assertThat(count).isEqualTo(1);
			assertThat(exported).containsExactly(view);
		}

		@Test
		void refusesAnInvertedDateRangeBeforeTheExportStarts() {
			SubscriptionSearchCriteria criteria = new SubscriptionSearchCriteria(null, null, null, null, TODAY,
					TODAY.minusDays(1));

			assertThatThrownBy(() -> subscriptionService.export(criteria))
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_DATE_RANGE);
			verifyNoInteractions(subscriptionQueries);
		}

		@Test
		void listsTheSubscriptionsOfAClient() {
			when(clientRepository.findById(1L)).thenReturn(Optional.of(
					Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", ClientStatus.ACTIVE, null)));
			Page<SubscriptionView> page = new PageImpl<>(List.of(view));
			when(subscriptionQueries.findByClient(1L, PageRequest.of(0, 20))).thenReturn(page);

			assertThat(subscriptionService.listByClient(1L, PageRequest.of(0, 20))).isSameAs(page);
		}

		@Test
		void reportsAnUnknownClientWhenListingItsSubscriptions() {
			when(clientRepository.findById(99L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> subscriptionService.listByClient(99L, PageRequest.of(0, 20)))
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_NOT_FOUND);
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
		when(technologyRepository.findByCode("FTTH"))
				.thenReturn(Optional.of(Technology.restore("FTTH", "Fibre optique", true, null)));
	}

}
