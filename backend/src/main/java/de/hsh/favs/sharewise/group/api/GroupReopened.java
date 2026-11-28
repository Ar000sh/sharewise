package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * An admin reopened a group that was closed for settlement (J12b): expenses can be
 * recorded and corrected again (L6).
 */
public record GroupReopened(GroupId groupId, UserId reopenedBy, Instant occurredAt) {

}
