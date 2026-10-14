package ci.orange.subscription.domain.subscription;

/**
 * Formats of the numbers the server assigns to a subscription (rules 16 and 17). Where unique
 * sequence values come from is an infrastructure concern.
 */
public final class SubscriptionNumbering {

	static final long MAX_ND_SEQUENCE = 99_999_999L;

	static final long MAX_REFERENCE_SEQUENCE = 999_999L;

	private SubscriptionNumbering() {
	}

	/** Ten digits starting with 27, e.g. 2700000001. */
	public static String nd(long sequence) {
		requireInRange(sequence, MAX_ND_SEQUENCE, "des ND");
		return "27%08d".formatted(sequence);
	}

	/** SUB-{year}-{6 digits}, e.g. SUB-2026-000001. */
	public static String reference(int year, long sequence) {
		requireInRange(sequence, MAX_REFERENCE_SEQUENCE, "des références de l'année " + year);
		return "SUB-%04d-%06d".formatted(year, sequence);
	}

	/** Running out of numbers is an operational incident, not a user error. */
	private static void requireInRange(long sequence, long max, String numbers) {
		if (sequence < 1 || sequence > max) {
			throw new IllegalStateException("Capacité de numérotation épuisée " + numbers);
		}
	}

}
