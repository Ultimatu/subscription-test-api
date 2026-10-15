package ci.orange.subscription.application.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Les champs modifiés par une action, avec leur valeur avant et après, pour le journal d'audit.
 */
final class Changes {

	private final Map<String, Object> changed = new LinkedHashMap<>();

	Changes track(String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("before", before);
			values.put("after", after);
			changed.put(field, values);
		}
		return this;
	}

	boolean isEmpty() {
		return changed.isEmpty();
	}

	Map<String, Object> asMap() {
		return changed;
	}

}
