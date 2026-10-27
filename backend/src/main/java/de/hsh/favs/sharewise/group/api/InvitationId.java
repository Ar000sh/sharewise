package de.hsh.favs.sharewise.group.api;

import java.util.UUID;

/** Identity of an invitation. Lives in {@code api} because events carry it. */
public record InvitationId(UUID value) {

}
