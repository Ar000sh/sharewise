package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/** A new group exists, OPEN, with its creator as first member. */
public record GroupCreated(GroupId groupId, UserId creator, Instant occurredAt) {

}
