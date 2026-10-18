package ci.orange.subscription.application.port.in;

import java.util.List;
import java.util.function.Consumer;

/**
 * Résultat d'un export, lu par lots au moment où on le parcourt : jamais plus d'un lot en mémoire,
 * quel que soit le nombre de lignes (ADR-0013). Les critères sont vérifiés avant que l'export soit
 * renvoyé, pour qu'une erreur de saisie donne une 400 avant que la réponse commence.
 */
@FunctionalInterface
public interface BatchedExport<T> {

	/** Passe chaque lot à {@code action}, dans l'ordre des identifiants ; renvoie le nombre d'éléments. */
	long forEachBatch(Consumer<List<T>> action);

}
