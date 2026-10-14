package ci.orange.subscription.infrastructure.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Charge un compte pour Spring Security au moment de la connexion. Un utilisateur inconnu et un
 * mauvais mot de passe produisent la même erreur, pour ne pas révéler quels comptes existent.
 */
@Service
class UserAccountDetailsService implements UserDetailsService {

	private final UserAccountJpaRepository repository;

	UserAccountDetailsService(UserAccountJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		return repository.findByUsername(username)
				.map(account -> User.withUsername(account.getUsername())
						.password(account.getPasswordHash())
						.roles(account.getRole().name())
						.disabled(!account.isEnabled())
						.build())
				.orElseThrow(() -> new UsernameNotFoundException("Utilisateur inconnu"));
	}

}
