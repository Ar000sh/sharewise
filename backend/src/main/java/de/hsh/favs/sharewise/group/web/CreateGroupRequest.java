package de.hsh.favs.sharewise.group.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /groups}. The length rule is enforced by {@code GroupName}. */
record CreateGroupRequest(@NotBlank String name) {

}
