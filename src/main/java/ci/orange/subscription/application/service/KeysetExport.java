package ci.orange.subscription.application.service;

import ci.orange.subscription.application.port.in.BatchedExport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Parcours par lots fondé sur l'identifiant (« keyset ») : chaque lot reprend après le dernier
 * identifiant lu. Contrairement à une pagination par numéro de page, une ligne ajoutée ou supprimée
 * pendant l'export ne décale pas les lots suivants, et chaque lot coûte une requête indexée, sans
 * comptage.
 */
final class KeysetExport {

	static final int BATCH_SIZE = 500;

	private KeysetExport() {
	}

	/**
	 * @param batchAfter lot suivant : les éléments d'identifiant strictement supérieur au premier
	 *        argument (nul au départ), triés par identifiant, au plus autant que le second argument
	 */
	static <T> BatchedExport<T> of(BiFunction<Long, Integer, List<T>> batchAfter, Function<T, Long> idOf) {
		return action -> {
			long count = 0;
			Long lastId = null;
			List<T> batch;
			do {
				batch = batchAfter.apply(lastId, BATCH_SIZE);
				if (!batch.isEmpty()) {
					action.accept(batch);
					count += batch.size();
					lastId = idOf.apply(batch.get(batch.size() - 1));
				}
			}
			while (batch.size() == BATCH_SIZE);
			return count;
		};
	}

	/**
	 * Noms des filtres renseignés, sans leurs valeurs, qui peuvent contenir des données personnelles :
	 * pour le journal d'audit. Les arguments alternent nom et valeur.
	 */
	static List<String> usedFilters(Object... namesAndValues) {
		List<String> used = new ArrayList<>();
		for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
			Object value = namesAndValues[i + 1];
			if (value != null && !value.toString().isBlank()) {
				used.add(namesAndValues[i].toString());
			}
		}
		return used;
	}

}
