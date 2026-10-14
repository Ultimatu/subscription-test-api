package ci.orange.subscription.infrastructure.persistence.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import ci.orange.subscription.application.port.out.SubscriptionNumberSequences;
import ci.orange.subscription.infrastructure.persistence.PersistenceAdapterTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class SubscriptionNumberSequencesAdapterTest {

	@Autowired
	private SubscriptionNumberSequences sequences;

	@Test
	void neverReturnsTheSameNdTwice() {
		long first = sequences.nextNd();
		long second = sequences.nextNd();

		assertThat(first).isPositive();
		assertThat(second).isEqualTo(first + 1);
	}

	@Test
	void countsReferencesFromOneForEveryYear() {
		// La table des compteurs est annulée après le test : chaque année repart donc de zéro ici
		assertThat(sequences.nextReference(2026)).isEqualTo(1);
		assertThat(sequences.nextReference(2026)).isEqualTo(2);
		assertThat(sequences.nextReference(2027)).isEqualTo(1);
		assertThat(sequences.nextReference(2026)).isEqualTo(3);
	}

}
