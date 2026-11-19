package de.hsh.favs.sharewise.account.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.application.AccountService;
import de.hsh.favs.sharewise.account.domain.DisplayName;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current account endpoints ({@code /me}). Sign-up and sign-in happen at Keycloak; the
 * first call here after a login also creates the account (D4).
 */
@RestController
class AccountController {

	private final CurrentUser currentUser;

	private final AccountService accounts;

	AccountController(CurrentUser currentUser, AccountService accounts) {
		this.currentUser = currentUser;
		this.accounts = accounts;
	}

	@GetMapping("/me")
	UserResponse me() {
		return UserResponse.of(currentUser.get());
	}

	@PatchMapping("/me")
	UserResponse changeDisplayName(@Valid @RequestBody ChangeDisplayNameRequest request) {
		return UserResponse
			.of(accounts.changeDisplayName(currentUser.get().userId(), new DisplayName(request.displayName())));
	}

}
