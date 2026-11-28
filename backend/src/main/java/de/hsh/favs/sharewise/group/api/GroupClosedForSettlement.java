package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;

/**
 * An admin closed the group for settlement (J12): expenses are frozen, only settlement
 * payments remain possible (L2, L3).
 */
public record GroupClosedForSettlement(GroupId groupId, UserId closedBy, Instant occurredAt) {

}
