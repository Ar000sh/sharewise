package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * An admin made an active member an admin too. Promotion is one way; there is no event
 * for the opposite.
 */
public record MemberPromoted(GroupId groupId, UserId userId, UserId promotedBy, Instant occurredAt) {

}
