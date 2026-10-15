package ci.orange.subscription.infrastructure.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les tâches planifiées ({@code @Scheduled}) : l'équivalent des crons dans l'application.
 * Chaque tâche a sa propre propriété d'activation.
 */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
