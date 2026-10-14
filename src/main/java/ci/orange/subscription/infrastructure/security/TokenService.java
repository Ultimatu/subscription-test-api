package ci.orange.subscription.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Émet le jeton d'accès d'un utilisateur authentifié : son nom dans {@code sub}, ses rôles dans
 * {@code roles}, signé avec la clé HMAC de l'application.
 */
@Component
public class TokenService {

	public static final String ROLES_CLAIM = "roles";

	private static final String ROLE_PREFIX = "ROLE_";

	private final JwtEncoder jwtEncoder;
	private final SecurityProperties properties;
	private final Clock clock;

	public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedToken issue(Authentication authentication) {
		Instant now = clock.instant();
		List<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.map(authority -> authority.startsWith(ROLE_PREFIX) ? authority.substring(ROLE_PREFIX.length())
						: authority)
				.toList();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().issuer())
				.subject(authentication.getName())
				.issuedAt(now)
				.expiresAt(now.plus(properties.jwt().validity()))
				.claim(ROLES_CLAIM, roles)
				.build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new IssuedToken(token, properties.jwt().validity().toSeconds(), roles);
	}

	/**
	 * @param value le jeton à transmettre dans l'en-tête Authorization: Bearer
	 * @param expiresIn durée de validité, en secondes
	 * @param roles rôles de l'utilisateur
	 */
	public record IssuedToken(String value, long expiresIn, List<String> roles) {
	}

}
