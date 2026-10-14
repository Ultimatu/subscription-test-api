package ci.orange.subscription.infrastructure.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRequestTest {

	@Test
	void neverShowsThePassword() {
		assertThat(new LoginRequest("admin", "Admin@2026"))
				.hasToString("LoginRequest[username=admin, password=***]");
	}

}
