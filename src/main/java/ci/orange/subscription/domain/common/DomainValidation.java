package ci.orange.subscription.domain.common;

import ci.orange.subscription.domain.exception.DomainValidationException;

public final class DomainValidation {

	private DomainValidation() {
	}

	public static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new DomainValidationException("Le champ " + field + " est obligatoire");
		}
		return value.trim();
	}

	public static <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new DomainValidationException("Le champ " + field + " est obligatoire");
		}
		return value;
	}

}
