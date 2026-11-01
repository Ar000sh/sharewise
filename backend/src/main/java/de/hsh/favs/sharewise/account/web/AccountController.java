package de.hsh.favs.sharewise.account.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I". Sign-up and sign-in happen at Keycloak; the first call here after a login
 * also creates the account (D4).
 */
@RestController
class AccountController {

	private final CurrentUser currentUser;

	AccountController(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	@GetMapping("/me")
	UserResponse me() {
		return UserResponse.of(currentUser.get());
	}

}
