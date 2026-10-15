package ci.orange.subscription.domain.technology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.domain.exception.BusinessRuleViolationException;
import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TechnologyTest {

	@ParameterizedTest
	@ValueSource(strings = { "FTTH", "SATELLITE", "4G_BOX", "5G_BOX" })
	void acceptsUpperCaseCodes(String code) {
		Technology technology = Technology.create(code, "Libellé");

		assertThat(technology.getCode()).isEqualTo(code);
		assertThat(technology.getLabel()).isEqualTo("Libellé");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "ftth", "4G-BOX", "4G BOX", "TOO_LONG_TECHNOLOGY_CODE" })
	void rejectsInvalidCodes(String code) {
		assertThatThrownBy(() -> Technology.create(code, "Libellé")).isInstanceOf(DomainValidationException.class);
	}

	@Test
	void identifiesAnInvalidCode() {
		assertThatThrownBy(() -> Technology.create("4G-BOX", "Box 4G"))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_TECHNOLOGY_CODE);
	}

	@Test
	void rejectsAMissingLabel() {
		assertThatThrownBy(() -> Technology.create("5G_BOX", " "))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("label");
	}

	@Test
	void isAvailableWhenCreatedAndCanBeRetiredThenRestored() {
		Technology technology = Technology.create("5G_BOX", "Box 5G");
		assertThat(technology.isActive()).isTrue();

		technology.deactivate();
		assertThat(technology.isActive()).isFalse();
		assertThatThrownBy(technology::ensureActive)
				.isInstanceOf(BusinessRuleViolationException.class)
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.TECHNOLOGY_INACTIVE)
				.hasMessageContaining("5G_BOX");

		technology.activate();
		assertThatCode(technology::ensureActive).doesNotThrowAnyException();
	}

}
