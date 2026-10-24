package de.hsh.favs.sharewise.group.api;

import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;

public record MemberJoined(GroupId groupId, MemberId memberId, Role role) {
}
