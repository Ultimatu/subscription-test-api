package ci.orange.subscription.infrastructure.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Comparaisons de texte insensibles à la casse et aux accents, grâce à l'extension PostgreSQL
 * unaccent (migration V6) : « Kone », « KONÉ » et « koné » sont équivalents.
 */
public final class TextSearch {

	private static final char LIKE_ESCAPE = '\\';

	private TextSearch() {
	}

	/**
	 * Forme comparable d'une valeur : sans accents, puis en minuscules. L'ordre compte : avec une base
	 * en locale « C », lower() ne traite que l'ASCII (lower('É') reste 'É'), d'où unaccent en premier.
	 */
	public static Expression<String> comparable(CriteriaBuilder builder, Expression<String> value) {
		return builder.lower(builder.function("unaccent", String.class, value));
	}

	public static Expression<String> comparable(CriteriaBuilder builder, String value) {
		return comparable(builder, builder.literal(value.trim()));
	}

	/** Égalité sans tenir compte de la casse ni des accents. */
	public static Predicate equal(CriteriaBuilder builder, Expression<String> field, String value) {
		return builder.equal(comparable(builder, field), comparable(builder, value));
	}

	/**
	 * Correspondance partielle sans tenir compte de la casse ni des accents. « % » et « _ » saisis
	 * par l'utilisateur sont recherchés tels quels, sans servir de jokers.
	 */
	public static Predicate contains(CriteriaBuilder builder, Expression<String> field, String value) {
		String pattern = "%" + escapeLikeWildcards(value.trim()) + "%";
		return builder.like(comparable(builder, field), comparable(builder, pattern), LIKE_ESCAPE);
	}

	private static String escapeLikeWildcards(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
