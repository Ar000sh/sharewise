package de.hsh.favs.sharewise.account.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.application.AccountService;
import de.hsh.favs.sharewise.account.application.LoginCommand;
import de.hsh.favs.sharewise.account.application.RegisterCommand;
import de.hsh.favs.sharewise.account.domain.DisplayName;
import de.hsh.favs.sharewise.account.domain.RawPassword;
import de.hsh.favs.sharewise.shared.Email;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign up, sign in and "who am I". */
@RestController
class AccountController {

	private final AccountService accounts;

	private final CurrentUser currentUser;

	AccountController(AccountService accounts, CurrentUser currentUser) {
		this.accounts = accounts;
		this.currentUser = currentUser;
	}

	@PostMapping("/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		var command = new RegisterCommand(new Email(request.email()), new DisplayName(request.displayName()),
				new RawPassword(request.password()));
		return AuthResponse.of(accounts.register(command));
	}

	@PostMapping("/auth/login")
	AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return AuthResponse.of(accounts.login(new LoginCommand(new Email(request.email()), request.password())));
	}

	@GetMapping("/me")
	UserResponse me() {
		return UserResponse.of(currentUser.get());
	}

}
