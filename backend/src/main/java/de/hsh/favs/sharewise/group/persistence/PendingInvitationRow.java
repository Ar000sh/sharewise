package de.hsh.favs.sharewise.group.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * One open invitation as the database answers it: just the columns the invitation list
 * shows, so no entity and no lazy collection is involved.
 */
public record PendingInvitationRow(UUID invitationId, UUID groupId, String groupName, UUID invitedBy,
		Instant expiresAt) {

}
