package ci.orange.subscription.domain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.domain.exception.DomainErrorCode;
import ci.orange.subscription.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientTest {

	@Test
	void createsAnActiveClientWithNormalizedEmail() {
		Client client = Client.create(" Awa ", "Koné", " Awa.Kone@Mail.CI ", "0701020304", "Abidjan");

		assertThat(client.getStatus()).isEqualTo(ClientStatus.ACTIVE);
		assertThat(client.getEmail()).isEqualTo("awa.kone@mail.ci");
		assertThat(client.getFirstName()).isEqualTo("Awa");
		assertThat(client.getId()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "0101020304", "0501020304", "0701020304" })
	void acceptsPhoneNumbersStartingWith01_05_or_07(String phoneNumber) {
		Client client = Client.create("Awa", "Koné", "awa@mail.ci", phoneNumber, "Abidjan");

		assertThat(client.getPhoneNumberClient()).isEqualTo(phoneNumber);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "0201020304", "070102030", "07010203045", "+2250701020304", "07 01 02 03 04", "07010203ab" })
	void rejectsInvalidPhoneNumbers(String phoneNumber) {
		assertThatThrownBy(() -> Client.create("Awa", "Koné", "awa@mail.ci", phoneNumber, "Abidjan"))
				.isInstanceOf(DomainValidationException.class);
	}

	@Test
	void identifiesAnInvalidPhoneNumber() {
		assertThatThrownBy(() -> Client.create("Awa", "Koné", "awa@mail.ci", "0201020304", "Abidjan"))
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.INVALID_PHONE_NUMBER);
	}

	@Test
	void rejectsMissingFirstName() {
		assertThatThrownBy(() -> Client.create(" ", "Koné", "awa@mail.ci", "0701020304", "Abidjan"))
				.isInstanceOf(DomainValidationException.class)
				.hasMessageContaining("firstName")
				.hasFieldOrPropertyWithValue("errorCode", DomainErrorCode.REQUIRED_FIELD);
	}

	@Test
	void updatesEverythingButTheEmail() {
		Client client = Client.create("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan");

		client.update("Aya", "Traoré", "0501020304", "Bouaké", ClientStatus.INACTIVE);

		assertThat(client.getFirstName()).isEqualTo("Aya");
		assertThat(client.getLastName()).isEqualTo("Traoré");
		assertThat(client.getPhoneNumberClient()).isEqualTo("0501020304");
		assertThat(client.getCity()).isEqualTo("Bouaké");
		assertThat(client.getEmail()).isEqualTo("awa@mail.ci");
		assertThat(client.isActive()).isFalse();
	}

	@Test
	void statusCanGoBackToActive() {
		Client client = Client.create("Awa", "Koné", "awa@mail.ci", "0701020304", "Abidjan");
		client.update("Awa", "Koné", "0701020304", "Abidjan", ClientStatus.INACTIVE);

		client.update("Awa", "Koné", "0701020304", "Abidjan", ClientStatus.ACTIVE);

		assertThat(client.isActive()).isTrue();
	}

}
