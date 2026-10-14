package ci.orange.subscription.domain.subscription;

import static ci.orange.subscription.domain.subscription.SubscriptionStatus.ACTIVE;
import static ci.orange.subscription.domain.subscription.SubscriptionStatus.PENDING;
import static ci.orange.subscription.domain.subscription.SubscriptionStatus.SUSPENDED;
import static ci.orange.subscription.domain.subscription.SubscriptionStatus.TERMINATED;
import static ci.orange.subscription.domain.subscription.SubscriptionTransition.ACTIVATE;
import static ci.orange.subscription.domain.subscription.SubscriptionTransition.REACTIVATE;
import static ci.orange.subscription.domain.subscription.SubscriptionTransition.SUSPEND;
import static ci.orange.subscription.domain.subscription.SubscriptionTransition.TERMINATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SubscriptionTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

	/** Le tableau des transitions de l'énoncé, réécrit indépendamment du code de production. */
	private static final Map<SubscriptionStatus, Map<SubscriptionTransition, SubscriptionStatus>> SPECIFICATION = Map.of(
			PENDING, Map.of(ACTIVATE, ACTIVE, TERMINATE, TERMINATED),
			ACTIVE, Map.of(SUSPEND, SUSPENDED, TERMINATE, TERMINATED),
			SUSPENDED, Map.of(REACTIVATE, ACTIVE, TERMINATE, TERMINATED),
			TERMINATED, Map.of());

	@Nested
	class Creation {

		@Test
		void startsPendingAndRecordsTheCreation() {
			Subscription subscription = Subscription.create("SUB-2026-000001", "2700000001", 1L, 2L, TODAY, TODAY);

			assertThat(subscription.getStatus()).isEqualTo(PENDING);
			assertThat(subscription.getEndDate()).isNull();
			assertThat(subscription.recordedStatusChanges()).containsExactly(new StatusChange(null, PENDING, null));
		}

		@Test
		void acceptsAFutureStartDate() {
			Subscription subscription = Subscription.create("SUB-2026-000001", "2700000001", 1L, 2L,
					TODAY.plusDays(10), TODAY);

			assertThat(subscription.getStartDate()).isEqualTo(TODAY.plusDays(10));
		}

		@Test
		void rejectsAStartDateBeforeToday() {
			assertThatThrownBy(
					() -> Subscription.create("SUB-2026-000001", "2700000001", 1L, 2L, TODAY.minusDays(1), TODAY))
					.isInstanceOf(DomainValidationException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.START_DATE_IN_PAST);
		}

	}

	@Nested
	class Transitions {

		static Stream<Arguments> everyStatusAndTransition() {
			return Arrays.stream(SubscriptionStatus.values())
					.flatMap(status -> Arrays.stream(SubscriptionTransition.values())
							.map(transition -> Arguments.of(status, transition)));
		}

		@ParameterizedTest(name = "{1} from {0}")
		@MethodSource("everyStatusAndTransition")
		void followsTheSpecificationTable(SubscriptionStatus initialStatus, SubscriptionTransition transition) {
			Subscription subscription = subscriptionIn(initialStatus);
			SubscriptionStatus expectedStatus = SPECIFICATION.get(initialStatus).get(transition);

			if (expectedStatus != null) {
				apply(subscription, transition);
				assertThat(subscription.getStatus()).isEqualTo(expectedStatus);
				assertThat(subscription.recordedStatusChanges()).hasSize(1).first()
						.satisfies(change -> {
							assertThat(change.from()).isEqualTo(initialStatus);
							assertThat(change.to()).isEqualTo(expectedStatus);
						});
			}
			else {
				assertThatThrownBy(() -> apply(subscription, transition))
						.isInstanceOf(InvalidStatusTransitionException.class);
				assertThat(subscription.getStatus()).isEqualTo(initialStatus);
				assertThat(subscription.recordedStatusChanges()).isEmpty();
			}
		}

		@Test
		void refusalStatesTheCurrentAndRequiredStatuses() {
			Subscription subscription = subscriptionIn(ACTIVE);

			assertThatThrownBy(subscription::activate)
					.isInstanceOf(InvalidStatusTransitionException.class)
					.isInstanceOf(BusinessRuleViolationException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_STATUS_TRANSITION)
					.hasMessageContaining("ACTIVE")
					.hasMessageContaining("PENDING")
					.satisfies(error -> {
						InvalidStatusTransitionException exception = (InvalidStatusTransitionException) error;
						assertThat(exception.getCurrentStatus()).isEqualTo(ACTIVE);
						assertThat(exception.getRequiredStatuses()).containsExactly(PENDING);
					});
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   " })
		void suspensionRequiresAReason(String reason) {
			Subscription subscription = subscriptionIn(ACTIVE);

			assertThatThrownBy(() -> subscription.suspend(reason)).isInstanceOf(DomainValidationException.class);
			assertThat(subscription.getStatus()).isEqualTo(ACTIVE);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   " })
		void terminationRequiresAReason(String reason) {
			Subscription subscription = subscriptionIn(ACTIVE);

			assertThatThrownBy(() -> subscription.terminate(reason, TODAY))
					.isInstanceOf(DomainValidationException.class);
			assertThat(subscription.getStatus()).isEqualTo(ACTIVE);
		}

		@Test
		void keepsTheReasonOfEachSuspension() {
			Subscription subscription = subscriptionIn(ACTIVE);

			subscription.suspend(" Impayé ");
			subscription.reactivate();
			subscription.suspend("Demande du client");

			assertThat(subscription.recordedStatusChanges()).containsExactly(
					new StatusChange(ACTIVE, SUSPENDED, "Impayé"),
					new StatusChange(SUSPENDED, ACTIVE, null),
					new StatusChange(ACTIVE, SUSPENDED, "Demande du client"));
		}

		@Test
		void terminationClosesTheSubscriptionToday() {
			Subscription subscription = subscriptionIn(SUSPENDED);

			subscription.terminate("Déménagement", TODAY);

			assertThat(subscription.getStatus()).isEqualTo(TERMINATED);
			assertThat(subscription.getEndDate()).isEqualTo(TODAY);
		}

	}

	@Nested
	class Deletion {

		@Test
		void pendingSubscriptionCanBeDeleted() {
			assertThatCode(() -> subscriptionIn(PENDING).ensureDeletable()).doesNotThrowAnyException();
		}

		@ParameterizedTest
		@EnumSource(value = SubscriptionStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
		void otherSubscriptionsCannotBeDeleted(SubscriptionStatus status) {
			assertThatThrownBy(() -> subscriptionIn(status).ensureDeletable())
					.isInstanceOf(BusinessRuleViolationException.class)
					.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.SUBSCRIPTION_NOT_DELETABLE)
					.hasMessageContaining(status.name());
		}

	}

	@ParameterizedTest
	@EnumSource(SubscriptionStatus.class)
	void isInProgressOnlyWhenPendingOrActive(SubscriptionStatus status) {
		assertThat(subscriptionIn(status).isInProgress()).isEqualTo(status == PENDING || status == ACTIVE);
	}

	private static Subscription subscriptionIn(SubscriptionStatus status) {
		return Subscription.restore(1L, "SUB-2026-000001", "2700000001", 1L, 2L, status, TODAY, null, null);
	}

	private static void apply(Subscription subscription, SubscriptionTransition transition) {
		switch (transition) {
			case ACTIVATE -> subscription.activate();
			case SUSPEND -> subscription.suspend("Motif");
			case REACTIVATE -> subscription.reactivate();
			case TERMINATE -> subscription.terminate("Motif", TODAY);
		}
	}

}
