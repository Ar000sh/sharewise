package de.hsh.favs.sharewise.account.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /auth/register}. Format and length rules are enforced by the value
 * objects.
 */
record RegisterRequest(@NotBlank String email, @NotBlank String displayName, @NotBlank String password) {

}
