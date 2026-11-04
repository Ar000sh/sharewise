package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * A person became an active member of a group, as creator (ADMIN) or through an
 * invitation (MEMBER). {@code invitedBy} is the member who sent the invitation, and
 * {@code null} for the creator, because nobody invited them.
 */
public record MemberJoined(GroupId groupId, UserId userId, GroupRole role, UserId invitedBy, Instant occurredAt) {

}
