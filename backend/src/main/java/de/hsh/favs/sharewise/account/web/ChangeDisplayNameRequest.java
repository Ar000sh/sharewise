package de.hsh.favs.sharewise.account.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code PATCH /me}. The length rule is enforced by {@code DisplayName}.
 */
record ChangeDisplayNameRequest(@NotBlank String displayName) {

}
