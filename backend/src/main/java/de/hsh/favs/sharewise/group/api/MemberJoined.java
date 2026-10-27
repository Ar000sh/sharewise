package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.shared.GroupId;

public record MemberJoined(GroupId groupId, UserId memberId, Role role) {
}
