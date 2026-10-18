package ci.orange.subscription.application.service;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;

/**
 * Politique de suppression et de conservation des données (ADR-0013), fixée par la configuration
 * ({@code app.deletion.*}).
 *
 * @param softDelete un client qui a un historique de souscriptions est supprimé logiquement :
 *        masqué dans l'API mais conservé ; sinon, il est supprimé définitivement
 * @param hardDelete la purge (suppression définitive) des clients conservés est permise
 * @param retentionEnabled la purge n'est permise qu'à l'issue du délai de conservation
 * @param retentionPeriod délai de conservation, compté depuis la suppression logique ; sa durée réelle
 *        est fixée par l'ARTCI pour chaque finalité (loi n° 2013-450, art. 43)
 */
public record DeletionPolicy(boolean softDelete, boolean hardDelete, boolean retentionEnabled,
		Period retentionPeriod) {

	/** Date à partir de laquelle un client supprimé logiquement peut être purgé. */
	public Instant purgeableFrom(Instant deletedAt, ZoneId zone) {
		return retentionEnabled ? deletedAt.atZone(zone).plus(retentionPeriod).toInstant() : deletedAt;
	}

	/** Les clients supprimés logiquement avant cette date peuvent être purgés à {@code now}. */
	Instant purgeableIfDeletedBefore(Instant now, ZoneId zone) {
		return retentionEnabled ? now.atZone(zone).minus(retentionPeriod).toInstant() : now;
	}

}
