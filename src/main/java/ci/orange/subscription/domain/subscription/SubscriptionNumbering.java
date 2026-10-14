package ci.orange.subscription.domain.subscription;

/**
 * Formats des numéros que le serveur attribue à une souscription (règles 16 et 17). L'obtention de
 * valeurs de séquence uniques relève de l'infrastructure.
 */
public final class SubscriptionNumbering {

	static final long MAX_ND_SEQUENCE = 99_999_999L;

	static final long MAX_REFERENCE_SEQUENCE = 999_999L;

	private SubscriptionNumbering() {
	}

	/** Dix chiffres commençant par 27, par exemple 2700000001. */
	public static String nd(long sequence) {
		requireInRange(sequence, MAX_ND_SEQUENCE, "des ND");
		return "27%08d".formatted(sequence);
	}

	/** SUB-{année}-{6 chiffres}, par exemple SUB-2026-000001. */
	public static String reference(int year, long sequence) {
		requireInRange(sequence, MAX_REFERENCE_SEQUENCE, "des références de l'année " + year);
		return "SUB-%04d-%06d".formatted(year, sequence);
	}

	/** Manquer de numéros est un incident d'exploitation, pas une erreur de l'utilisateur. */
	private static void requireInRange(long sequence, long max, String numbers) {
		if (sequence < 1 || sequence > max) {
			throw new IllegalStateException("Capacité de numérotation épuisée " + numbers);
		}
	}

}
