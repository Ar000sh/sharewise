package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.UserDirectory;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Powers the dev user switcher. Exists only under the dev profile. */
@RestController
@Profile("dev")
class DevUsersController {

	private final UserDirectory users;

	DevUsersController(UserDirectory users) {
		this.users = users;
	}

	@GetMapping("/users")
	List<AccountController.UserResponse> users() {
		return users.all().stream().map(AccountController.UserResponse::of).toList();
	}

}
