package ci.orange.subscription.infrastructure.scheduling;

import ci.orange.subscription.application.port.in.client.ClientDeletionUseCases;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purge chaque nuit les clients dont le délai de conservation est écoulé (ADR-0013) : sans cette
 * routine, des données seraient conservées au-delà du nécessaire (loi n° 2013-450, art. 16). Active
 * seulement avec la rétention ; sans rétention, la purge est manuelle.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.deletion.retention", name = "enabled", havingValue = "true")
class RetentionPurgeScheduler {

	private final ClientDeletionUseCases clientDeletionUseCases;

	RetentionPurgeScheduler(ClientDeletionUseCases clientDeletionUseCases) {
		this.clientDeletionUseCases = clientDeletionUseCases;
	}

	@Scheduled(cron = "${app.deletion.retention.purge-cron}", zone = "Africa/Abidjan")
	void purgeExpired() {
		int purged = clientDeletionUseCases.purgeExpired();
		if (purged > 0) {
			log.info("Retention purge: {} client(s) permanently deleted", purged);
		}
	}

}
