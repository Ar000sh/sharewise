package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * A person became an active member of a group, as creator (ADMIN) or through an
 * invitation (MEMBER).
 */
public record MemberJoined(GroupId groupId, UserId userId, GroupRole role, Instant occurredAt) {

}
