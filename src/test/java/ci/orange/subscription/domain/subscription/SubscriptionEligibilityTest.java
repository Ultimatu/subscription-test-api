package ci.orange.subscription.domain.subscription;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.domain.client.Client;
import ci.orange.subscription.domain.client.ClientStatus;
import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.offer.Offer;
import ci.orange.subscription.domain.technology.Technology;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SubscriptionEligibilityTest {

	private static final Client ACTIVE_CLIENT = client(ClientStatus.ACTIVE);
	private static final Client INACTIVE_CLIENT = client(ClientStatus.INACTIVE);
	private static final Offer ACTIVE_OFFER = offer(true);
	private static final Offer INACTIVE_OFFER = offer(false);
	private static final Technology AVAILABLE = Technology.restore("FTTH", "Fibre optique", true, null);
	private static final Technology RETIRED = Technology.restore("FTTH", "Fibre optique", false, null);

	@Test
	void acceptsAnActiveClientOnAnActiveOfferWithoutConflict() {
		assertThatCode(() -> SubscriptionEligibility.check(ACTIVE_CLIENT, ACTIVE_OFFER, AVAILABLE, false, false))
				.doesNotThrowAnyException();
	}

	/**
	 * Chaque cas enfreint toutes les règles à partir de celle attendue : seule la première dans
	 * l'ordre doit être signalée.
	 */
	static Stream<Arguments> rulesInTheImposedOrder() {
		return Stream.of(
				Arguments.of(INACTIVE_CLIENT, INACTIVE_OFFER, RETIRED, true, true, DomainErrorCode.CLIENT_INACTIVE),
				Arguments.of(ACTIVE_CLIENT, INACTIVE_OFFER, RETIRED, true, true, DomainErrorCode.OFFER_INACTIVE),
				Arguments.of(ACTIVE_CLIENT, ACTIVE_OFFER, RETIRED, true, true, DomainErrorCode.TECHNOLOGY_INACTIVE),
				Arguments.of(ACTIVE_CLIENT, ACTIVE_OFFER, AVAILABLE, true, true,
						DomainErrorCode.CLIENT_HAS_SUSPENDED_SUBSCRIPTION),
				Arguments.of(ACTIVE_CLIENT, ACTIVE_OFFER, AVAILABLE, false, true,
						DomainErrorCode.TECHNOLOGY_ALREADY_SUBSCRIBED));
	}

	@ParameterizedTest(name = "{5}")
	@MethodSource("rulesInTheImposedOrder")
	void reportsTheFirstBrokenRuleInTheOrderOfRule15(Client client, Offer offer, Technology technology,
			boolean hasSuspended, boolean hasOngoingOnTechnology, DomainErrorCode expected) {
		assertThatThrownBy(() -> SubscriptionEligibility.check(client, offer, technology, hasSuspended,
				hasOngoingOnTechnology))
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", expected);
	}

	@Test
	void aSuspendedClientAskingForATechnologyAlreadyHeldGetsTheSuspensionError() {
		// L'exemple donné par la règle 15
		assertThatThrownBy(() -> SubscriptionEligibility.check(ACTIVE_CLIENT, ACTIVE_OFFER, AVAILABLE, true, true))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_HAS_SUSPENDED_SUBSCRIPTION);
	}

	@Test
	void aSuspendedSubscriptionBlocksEveryTechnology() {
		assertThatThrownBy(() -> SubscriptionEligibility.check(ACTIVE_CLIENT, ACTIVE_OFFER, AVAILABLE, true, false))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.CLIENT_HAS_SUSPENDED_SUBSCRIPTION);
	}

	private static Client client(ClientStatus status) {
		return Client.restore(1L, "Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan", status, null);
	}

	private static Offer offer(boolean active) {
		return Offer.restore(2L, "FTTH-100", "Fibre Confort 100 Mbps", "FTTH", new BigDecimal("25000.00"), active,
				null);
	}

}
