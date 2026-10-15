package ci.orange.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class KeysetExportTest {

	private final List<Long> requestedAfter = new ArrayList<>();
	private final List<Integer> batchSizes = new ArrayList<>();

	@Test
	void readsBatchAfterBatchFromTheLastIdentifierUntilABatchIsIncomplete() {
		long count = KeysetExport.of(this::upTo1201, id -> id).forEachBatch(batch -> batchSizes.add(batch.size()));

		assertThat(count).isEqualTo(1201);
		assertThat(batchSizes).containsExactly(500, 500, 201);
		assertThat(requestedAfter).containsExactly(null, 500L, 1000L);
	}

	@Test
	void stopsOnAnEmptyBatchWhenTheLastOneWasFull() {
		long count = KeysetExport.<Long>of((after, size) -> ids(after, size, 1000), id -> id)
				.forEachBatch(batch -> batchSizes.add(batch.size()));

		assertThat(count).isEqualTo(1000);
		assertThat(batchSizes).containsExactly(500, 500);
	}

	@Test
	void exportsNothingWhenNothingMatches() {
		long count = KeysetExport.<Long>of((after, size) -> List.of(), id -> id)
				.forEachBatch(batch -> batchSizes.add(batch.size()));

		assertThat(count).isZero();
		assertThat(batchSizes).isEmpty();
	}

	private List<Long> upTo1201(Long after, int size) {
		requestedAfter.add(after);
		return ids(after, size, 1201);
	}

	/** Identifiants 1 à {@code total}, après {@code after}, au plus {@code size}. */
	private static List<Long> ids(Long after, int size, long total) {
		long first = after == null ? 1 : after + 1;
		return LongStream.rangeClosed(first, Math.min(total, first + size - 1)).boxed().toList();
	}

}
