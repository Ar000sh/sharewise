package de.hsh.favs.sharewise.account.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /auth/login}. */
record LoginRequest(@NotBlank String email, @NotBlank String password) {

}
