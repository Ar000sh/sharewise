package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * An admin removed a member from the group (J11). The removed person is a former member
 * from now on and stays visible in old expenses (R13).
 */
public record MemberRemoved(GroupId groupId, UserId userId, UserId removedBy, Instant occurredAt) {

}
