package ci.orange.subscription.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.orange.subscription.infrastructure.security.TokenService.IssuedToken;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

	private static final SecretKey KEY = key((byte) 1);

	private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	private final TokenService tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
			new SecurityProperties(true, new SecurityProperties.Jwt("subscription-api", Duration.ofHours(1),
					Base64.getEncoder().encodeToString(KEY.getEncoded()))),
			Clock.fixed(now, ZoneOffset.UTC));

	@Test
	void issuesASignedTokenCarryingTheNameTheRolesAndTheExpiry() {
		IssuedToken issued = tokenService.issue(conseiller());

		Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(issued.value());

		assertThat(jwt.getSubject()).isEqualTo("conseiller");
		assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly("CONSEILLER");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("subscription-api");
		assertThat(jwt.getIssuedAt()).isEqualTo(now);
		assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(1, ChronoUnit.HOURS));
		assertThat(issued.expiresIn()).isEqualTo(3600);
		assertThat(issued.roles()).containsExactly("CONSEILLER");
	}

	@Test
	void aTokenSignedWithAnotherKeyIsRejected() {
		IssuedToken issued = tokenService.issue(conseiller());

		assertThatThrownBy(() -> NimbusJwtDecoder.withSecretKey(key((byte) 2)).macAlgorithm(MacAlgorithm.HS256).build()
				.decode(issued.value()))
				.isInstanceOf(JwtException.class);
	}

	private static UsernamePasswordAuthenticationToken conseiller() {
		return UsernamePasswordAuthenticationToken.authenticated("conseiller", null,
				List.of(new SimpleGrantedAuthority("ROLE_CONSEILLER")));
	}

	private static SecretKey key(byte fill) {
		byte[] bytes = new byte[32];
		Arrays.fill(bytes, fill);
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
