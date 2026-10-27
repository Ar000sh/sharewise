package de.hsh.favs.sharewise.group;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.group.api.Role;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GroupController {

	private final GroupService groups;

	private final CurrentUser currentUser;

	private final UserProfiles users;

	GroupController(GroupService groups, CurrentUser currentUser, UserProfiles users) {
		this.groups = groups;
		this.currentUser = currentUser;
		this.users = users;
	}

	record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {
	}

	record GroupResponse(UUID id, String name, GroupStatus status, int memberCount) {

		static GroupResponse of(GroupService.GroupView g) {
			return new GroupResponse(g.id(), g.name(), g.status(), g.memberCount());
		}

	}

	record MemberResponse(UUID memberId, String displayName, Role role) {
	}

	record GroupDetailResponse(UUID id, String name, GroupStatus status, int memberCount,
			List<MemberResponse> members) {
	}

	@PostMapping("/groups")
	@ResponseStatus(HttpStatus.CREATED)
	GroupResponse create(@Valid @RequestBody CreateGroupRequest request) {
		UserProfile caller = currentUser.get();
		return GroupResponse.of(groups.create(request.name().strip(), caller));
	}

	@GetMapping("/groups")
	List<GroupResponse> list() {
		return groups.listFor(currentUser.get().userId()).stream().map(GroupResponse::of).toList();
	}

	@GetMapping("/groups/{groupId}")
	GroupDetailResponse detail(@PathVariable UUID groupId) {
		UserId caller = currentUser.get().userId();
		GroupService.GroupView g = groups.detail(groupId, caller);
		List<MemberResponse> members = g.members()
			.stream()
			.map(m -> new MemberResponse(m.memberId(), displayName(m.memberId()), m.role()))
			.toList();
		return new GroupDetailResponse(g.id(), g.name(), g.status(), g.memberCount(), members);
	}

	private String displayName(UUID memberId) {
		return users.find(new UserId(memberId)).map(UserProfile::displayName).orElse("Unknown user");
	}

}
