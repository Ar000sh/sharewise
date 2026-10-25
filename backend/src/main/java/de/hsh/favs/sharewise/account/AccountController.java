package de.hsh.favs.sharewise.account;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserInfo;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {

	private final CurrentUser currentUser;

	AccountController(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	record UserResponse(UUID userId, String displayName) {

		static UserResponse of(UserInfo u) {
			return new UserResponse(u.id().value(), u.displayName());
		}

	}

	@GetMapping("/me")
	UserResponse me() {
		return UserResponse.of(currentUser.get());
	}

}
