package ci.orange.subscription.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriétés {@code app.cors.*} de application.yml.
 *
 * @param allowedOrigins origines (schéma, hôte et port) autorisées à appeler l'API depuis un navigateur,
 *        comme un back-office publié sur un autre domaine ; aucune par défaut
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
