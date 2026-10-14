package ci.orange.subscription.domain.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OfferTest {

	@Test
	void createsAnActiveOffer() {
		Offer offer = Offer.create("FTTH-100", "Fibre Confort 100 Mbps", "FTTH", new BigDecimal("25000"));

		assertThat(offer.isActive()).isTrue();
		assertThat(offer.getCode()).isEqualTo("FTTH-100");
		assertThat(offer.getTechnology()).isEqualTo("FTTH");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ftth-100", "Ftth-100", "FTTH 100", "FTTH-", "-FTTH" })
	void rejectsCodesThatAreNotUpperCase(String code) {
		assertThatThrownBy(() -> Offer.create(code, "Fibre", "FTTH", new BigDecimal("25000")))
				.isInstanceOf(DomainValidationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_OFFER_CODE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "-0.01" })
	void rejectsPricesThatAreNotStrictlyPositive(String price) {
		assertThatThrownBy(() -> Offer.create("FTTH-100", "Fibre", "FTTH", new BigDecimal(price)))
				.isInstanceOf(DomainValidationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_MONTHLY_PRICE);
	}

	@Test
	void storesPricesWithTwoDecimals() {
		Offer offer = Offer.create("FTTH-100", "Fibre", "FTTH", new BigDecimal("25000"));
		offer.update("Fibre", new BigDecimal("27000.5"));

		assertThat(offer.getMonthlyPrice()).isEqualTo(new BigDecimal("27000.50"));
		assertThat(Offer.create("FTTH-100", "Fibre", "FTTH", new BigDecimal("25000")).getMonthlyPrice())
				.isEqualTo(new BigDecimal("25000.00"));
	}

	@Test
	void refusesMoreThanTwoDecimalsRatherThanRounding() {
		assertThatThrownBy(() -> Offer.create("FTTH-100", "Fibre", "FTTH", new BigDecimal("25000.555")))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_MONTHLY_PRICE)
				.hasMessageContaining("deux décimales");
	}

	@Test
	void rejectsMissingTechnology() {
		assertThatThrownBy(() -> Offer.create("FTTH-100", "Fibre", null, new BigDecimal("25000")))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("technology");
	}

	@Test
	void updatesLabelAndPriceOnly() {
		Offer offer = Offer.create("FTTH-100", "Fibre Confort", "FTTH", new BigDecimal("25000"));

		offer.update("Fibre Confort Plus", new BigDecimal("27000"));

		assertThat(offer.getLabel()).isEqualTo("Fibre Confort Plus");
		assertThat(offer.getMonthlyPrice()).isEqualByComparingTo("27000");
		assertThat(offer.getCode()).isEqualTo("FTTH-100");
		assertThat(offer.getTechnology()).isEqualTo("FTTH");
	}

	@Test
	void deactivates() {
		Offer offer = Offer.create("FTTH-100", "Fibre Confort", "FTTH", new BigDecimal("25000"));

		offer.deactivate();

		assertThat(offer.isActive()).isFalse();
	}

}
