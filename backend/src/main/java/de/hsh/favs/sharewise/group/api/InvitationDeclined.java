package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/** The invited account turned the invitation down; nobody joined. */
public record InvitationDeclined(GroupId groupId, InvitationId invitationId, UserId invitee, UserId invitedBy,
		Instant occurredAt) {

}
