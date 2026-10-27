package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.group.application.GroupService;
import de.hsh.favs.sharewise.group.application.GroupService.GroupView;
import de.hsh.favs.sharewise.group.domain.GroupName;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Groups, their members and the invitation links. */
@RestController
class GroupController {

	private static final String UNKNOWN_NAME = "Unknown user";

	private final GroupService groups;

	private final CurrentUser currentUser;

	private final UserProfiles profiles;

	GroupController(GroupService groups, CurrentUser currentUser, UserProfiles profiles) {
		this.groups = groups;
		this.currentUser = currentUser;
		this.profiles = profiles;
	}

	@PostMapping("/groups")
	@ResponseStatus(HttpStatus.CREATED)
	GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
		return GroupResponse.of(groups.createGroup(new GroupName(request.name()), caller()));
	}

	@GetMapping("/groups")
	List<GroupResponse> listGroups() {
		return groups.listGroups(caller()).stream().map(GroupResponse::of).toList();
	}

	@GetMapping("/groups/{groupId}")
	GroupDetailResponse getGroup(@PathVariable UUID groupId) {
		GroupView group = groups.getGroup(new GroupId(groupId), caller());
		Set<UserId> memberIds = group.memberships()
			.stream()
			.map(GroupService.MembershipView::userId)
			.collect(Collectors.toSet());
		Map<UserId, String> names = profiles.findProfiles(memberIds)
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, UserProfile::displayName, (a, b) -> a));
		return GroupDetailResponse.of(group, names, UNKNOWN_NAME);
	}

	@PostMapping("/groups/{groupId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	InvitationResponse inviteMember(@PathVariable UUID groupId) {
		return InvitationResponse.of(groups.inviteMember(new GroupId(groupId), caller()));
	}

	@PostMapping("/invitations/{token}/accept")
	GroupResponse acceptInvitation(@PathVariable String token) {
		return GroupResponse.of(groups.acceptInvitation(token, caller()));
	}

	private UserId caller() {
		return currentUser.get().userId();
	}

}
