package ci.orange.subscription.infrastructure.security;

import ci.orange.subscription.infrastructure.web.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentification par jeton JWT et droits par rôle (ADR-0013). L'API est sans état : aucune session,
 * chaque requête porte son jeton. Avec {@code app.security.enabled=false}, toute l'API est ouverte.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	private static final String ADMIN = UserRole.ADMIN.name();
	private static final String CONSEILLER = UserRole.CONSEILLER.name();

	private static final String[] PUBLIC_PATHS = { "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
			"/actuator/health", "/error" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
			ProblemDetailFactory problems, ObjectMapper objectMapper) throws Exception {
		ProblemDetailsSecurityHandler errors = new ProblemDetailsSecurityHandler(problems, objectMapper);
		http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		if (!properties.enabled()) {
			// Signalé à chaque démarrage : une désactivation oubliée en production ne doit pas passer inaperçue
			log.warn("Security is DISABLED (app.security.enabled=false): the whole API is open without "
					+ "authentication. Never use this setting outside of local testing.");
			return http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll()).build();
		}
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers(PUBLIC_PATHS).permitAll()
						// Pilotage, suppressions et catalogue : administrateurs uniquement
						.requestMatchers(HttpMethod.GET, "/api/stats/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.POST, "/api/offers/**", "/api/technologies/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.PUT, "/api/offers/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.PATCH, "/api/offers/**").hasRole(ADMIN)
						// Tout le reste de l'API : clients et souscriptions au quotidien
						.requestMatchers("/api/**").hasAnyRole(ADMIN, CONSEILLER)
						.anyRequest().permitAll())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(errors))
				.build();
	}

	/** Clé HMAC partagée par l'émission et la vérification des jetons. */
	@Bean
	SecretKey jwtSigningKey(SecurityProperties properties) {
		byte[] key = Base64.getDecoder().decode(properties.jwt().secret());
		if (key.length < 32) {
			throw new IllegalStateException("La clé de signature des jetons doit faire au moins 256 bits");
		}
		return new SecretKeySpec(key, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	/** Vérifie la signature, la date d'expiration et l'émetteur de chaque jeton reçu. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
		return decoder;
	}

	/** Les rôles du jeton (claim « roles ») deviennent des autorités ROLE_… ; le nom vient de « sub ». */
	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
