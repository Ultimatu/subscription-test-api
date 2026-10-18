package ci.orange.subscription.infrastructure.security;

import ci.orange.subscription.infrastructure.web.error.ProblemDetailFactory;
import ci.orange.subscription.infrastructure.web.tracing.RequestTracingFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Authentification par jeton JWT et droits par rôle (ADR-0013). L'API est sans état : aucune session,
 * chaque requête porte son jeton. Avec {@code app.security.enabled=false}, toute l'API est ouverte.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ SecurityProperties.class, CorsProperties.class })
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
				.cors(Customizer.withDefaults())
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
						// Exports : des données personnelles en masse (ADR-0013)
						.requestMatchers(HttpMethod.GET, "/api/*/export").hasRole(ADMIN)
						// Journal d'audit (ADR-0013)
						.requestMatchers("/api/audit-events/**").hasRole(ADMIN)
						// Clients supprimés, en attente de purge (ADR-0013)
						.requestMatchers(HttpMethod.GET, "/api/clients/deleted").hasRole(ADMIN)
						.requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.POST, "/api/offers/**", "/api/technologies/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.PUT, "/api/offers/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.PATCH, "/api/offers/**", "/api/technologies/**").hasRole(ADMIN)
						// Tout le reste de l'API : clients et souscriptions au quotidien
						.requestMatchers("/api/**").hasAnyRole(ADMIN, CONSEILLER)
						.anyRequest().permitAll())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(errors))
				.build();
	}

	/**
	 * Origines autorisées à appeler l'API depuis un navigateur ({@code app.cors.allowed-origins}), comme le
	 * back-office publié sur un autre domaine. Sans origine configurée, aucune règle n'est déclarée et l'API
	 * se comporte comme avant. Le jeton circule dans l'en-tête Authorization, jamais dans un cookie : aucun
	 * identifiant n'est partagé. Les en-têtes exposés permettent au navigateur de lire l'identifiant de la
	 * requête, l'adresse d'une ressource créée et le nom des fichiers téléchargés.
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (!properties.allowedOrigins().isEmpty()) {
			CorsConfiguration configuration = new CorsConfiguration();
			configuration.setAllowedOrigins(properties.allowedOrigins());
			configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
			configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
					RequestTracingFilter.REQUEST_ID_HEADER));
			configuration.setExposedHeaders(List.of(RequestTracingFilter.REQUEST_ID_HEADER, HttpHeaders.LOCATION,
					HttpHeaders.CONTENT_DISPOSITION));
			configuration.setMaxAge(Duration.ofHours(1));
			source.registerCorsConfiguration("/api/**", configuration);
		}
		return source;
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
