package ci.orange.subscription.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubscriptionNumberingTest {

	@Test
	void formatsTheNdAsTenDigitsStartingWith27() {
		assertThat(SubscriptionNumbering.nd(1)).isEqualTo("2700000001");
		assertThat(SubscriptionNumbering.nd(SubscriptionNumbering.MAX_ND_SEQUENCE)).isEqualTo("2799999999");
		assertThat(SubscriptionNumbering.nd(42)).matches("^27[0-9]{8}$");
	}

	@Test
	void formatsTheReferenceWithTheYearAndSixDigits() {
		assertThat(SubscriptionNumbering.reference(2026, 1)).isEqualTo("SUB-2026-000001");
		assertThat(SubscriptionNumbering.reference(2027, SubscriptionNumbering.MAX_REFERENCE_SEQUENCE))
				.isEqualTo("SUB-2027-999999")
				.matches("^SUB-[0-9]{4}-[0-9]{6}$");
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1, 100_000_000 })
	void refusesAnNdOutOfCapacity(long sequence) {
		assertThatThrownBy(() -> SubscriptionNumbering.nd(sequence))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("épuisée");
	}

	@Test
	void refusesAReferenceBeyondTheYearlyCapacity() {
		assertThatThrownBy(() -> SubscriptionNumbering.reference(2026, 1_000_000))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("2026");
	}

}
