package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One accepted close or reopen; event id makes notification delivery idempotent. */
public record GroupLifecycleChanged(UUID id, GroupId groupId, UserId actor, GroupStatus status, List<UserId> recipients,
		Instant occurredAt) {
}
