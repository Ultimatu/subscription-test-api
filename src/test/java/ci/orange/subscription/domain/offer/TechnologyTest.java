package ci.orange.subscription.domain.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TechnologyTest {

	@Test
	void exposes4gBoxWithItsSpecificationCode() {
		assertThat(Technology.BOX_4G.getCode()).isEqualTo("4G_BOX");
		assertThat(Technology.fromCode("4G_BOX")).isEqualTo(Technology.BOX_4G);
	}

	@ParameterizedTest
	@EnumSource(Technology.class)
	void codeRoundTrips(Technology technology) {
		assertThat(Technology.fromCode(technology.getCode())).isEqualTo(technology);
	}

	@Test
	void rejectsUnknownCodes() {
		assertThatThrownBy(() -> Technology.fromCode("BOX_4G"))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("4G_BOX");
	}

}
