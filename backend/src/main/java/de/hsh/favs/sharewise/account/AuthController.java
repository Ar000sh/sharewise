package de.hsh.favs.sharewise.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign up and sign in with email and password. */
@RestController
class AuthController {

	private final AuthService auth;

	AuthController(AuthService auth) {
		this.auth = auth;
	}

	record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 100) String displayName, @NotBlank @Size(min = 8, max = 72) String password) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	record AuthResult(String token, UUID userId, String displayName) {

		static AuthResult of(AuthService.SignedIn s) {
			return new AuthResult(s.token(), s.user().id().value(), s.user().displayName());
		}

	}

	@PostMapping("/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResult register(@Valid @RequestBody RegisterRequest request) {
		return AuthResult.of(auth.register(request.email(), request.displayName(), request.password()));
	}

	@PostMapping("/auth/login")
	AuthResult login(@Valid @RequestBody LoginRequest request) {
		return AuthResult.of(auth.login(request.email(), request.password()));
	}

}
