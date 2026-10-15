package ci.orange.subscription.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.domain.subscription.SubscriptionStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttestationModelTest {

	private static final String NBSP = "\u00A0";

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "25000.00 | 25 000 FCFA", "150000 | 150 000 FCFA", "12000.50 | 12 000,50 FCFA",
			"999 | 999 FCFA" })
	void formatsPricesInFcfaWithNonBreakingSpaces(BigDecimal amount, String expected) {
		assertThat(AttestationModel.price(amount)).isEqualTo(expected.replace(" ", NBSP));
	}

	@Test
	void groupsNumbersInPairs() {
		assertThat(AttestationModel.inPairs("2700000001")).isEqualTo("27 00 00 00 01".replace(" ", NBSP));
		assertThat(AttestationModel.inPairs("0701020304")).isEqualTo("07 01 02 03 04".replace(" ", NBSP));
	}

	@Test
	void describesAnOngoingSubscription() {
		AttestationModel model = AttestationModel.from(AttestationFixtures.content(SubscriptionStatus.ACTIVE));

		assertThat(model.title()).isEqualTo("Attestation de souscription");
		assertThat(model.statusLabel()).isEqualTo("Active");
		assertThat(model.statusCss()).isEqualTo("active");
		assertThat(model.holderName()).isEqualTo("Awa Koné");
		assertThat(model.monthlyPrice()).isEqualTo("25 000 FCFA".replace(" ", NBSP));
		assertThat(model.startDate()).isEqualTo("15 octobre 2026");
		assertThat(model.endDate()).isNull();
		assertThat(model.terminationReason()).isNull();
		assertThat(model.generatedAt()).isEqualTo("20 octobre 2026 à 09:30");
	}

	@Test
	void usesTheFrozenPriceRatherThanTheCurrentCataloguePrice() {
		AttestationModel model = AttestationModel.from(AttestationFixtures.content(SubscriptionStatus.ACTIVE));

		assertThat(model.monthlyPrice()).doesNotContain("27");
	}

	@Test
	void namesEachOperationOfTheHistory() {
		AttestationModel model = AttestationModel.from(AttestationFixtures.content(SubscriptionStatus.TERMINATED));

		assertThat(model.history()).extracting(AttestationModel.HistoryRow::operation)
				.containsExactly("Souscription", "Activation", "Suspension", "Réactivation", "Résiliation");
		assertThat(model.history().get(2)).isEqualTo(
				new AttestationModel.HistoryRow("17/10/2026 10:00", "Suspension", "Suspendue", "Impayé", "conseiller"));
	}

	@Test
	void becomesATerminationCertificateOnceTerminated() {
		AttestationModel model = AttestationModel.from(AttestationFixtures.content(SubscriptionStatus.TERMINATED));

		assertThat(model.title()).isEqualTo("Attestation de résiliation");
		assertThat(model.statusLabel()).isEqualTo("Résiliée");
		assertThat(model.endDate()).isEqualTo("20 octobre 2026");
		assertThat(model.terminationReason()).isEqualTo("Déménagement");
	}

	@Test
	void labelsEveryStatusInFrench() {
		assertThat(AttestationModel.statusLabel(SubscriptionStatus.PENDING)).isEqualTo("En attente");
		assertThat(AttestationModel.statusLabel(SubscriptionStatus.SUSPENDED)).isEqualTo("Suspendue");
	}

}
