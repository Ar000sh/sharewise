package de.hsh.favs.sharewise.group.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupMembershipTest {

	GroupId known = new GroupId(UUID.randomUUID());

	MemberId member = new MemberId(UUID.randomUUID());

	MemberId stranger = new MemberId(UUID.randomUUID());

	GroupMembership inMemory = new GroupMembership() {
		Map<GroupId, List<MemberId>> groups = Map.of(known, List.of(member));

		public boolean exists(GroupId g) {
			return groups.containsKey(g);
		}

		public boolean isMember(GroupId g, MemberId m) {
			return groups.getOrDefault(g, List.of()).contains(m);
		}

		public List<MemberId> members(GroupId g) {
			return groups.getOrDefault(g, List.of());
		}

		public GroupStatus status(GroupId g) {
			return GroupStatus.OPEN;
		}
	};

	@Test
	void requireMemberRejectsUnknownGroupAndNonMember() {
		assertThatThrownBy(() -> inMemory.requireMember(new GroupId(UUID.randomUUID()), member)).isInstanceOfSatisfying(
				DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.GROUP_NOT_FOUND));
		assertThatThrownBy(() -> inMemory.requireMember(known, stranger)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		assertThatCode(() -> inMemory.requireMember(known, member)).doesNotThrowAnyException();
	}

}
