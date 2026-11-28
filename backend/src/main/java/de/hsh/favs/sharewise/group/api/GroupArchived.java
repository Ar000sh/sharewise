package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * The group is archived: final and read-only (J13, L6). {@code archivedBy} is the admin
 * who archived it, or null when it happened automatically because the last balance
 * reached 0 (L5).
 */
public record GroupArchived(GroupId groupId, UserId archivedBy, Instant occurredAt) {

}
