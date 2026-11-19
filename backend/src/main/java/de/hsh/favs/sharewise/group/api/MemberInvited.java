package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * An admin invited an existing account to the group, to join with {@code role}. Nothing
 * consumes it yet; a later email channel would.
 */
public record MemberInvited(GroupId groupId, InvitationId invitationId, UserId invitee, UserId invitedBy,
		GroupRole role, Instant occurredAt) {

}
