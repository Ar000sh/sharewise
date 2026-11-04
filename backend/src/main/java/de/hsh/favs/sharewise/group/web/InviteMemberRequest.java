package de.hsh.favs.sharewise.group.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /groups/{groupId}/invitations}: an exact username or email. */
record InviteMemberRequest(@NotBlank @Size(max = 254) String identifier) {

}
