package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.Email;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * A member invited somebody to the group. {@code email} is null for a link invitation
 * that anyone who has the link can use.
 */
public record MemberInvited(GroupId groupId, InvitationId invitationId, Email email, UserId invitedBy,
		Instant occurredAt) {

}
