package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * A member left the group on their own (J10). They are a former member from now on and
 * stay visible in old expenses (R13).
 */
public record MemberLeft(GroupId groupId, UserId userId, Instant occurredAt) {

}
