package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.group.application.GroupService;
import de.hsh.favs.sharewise.group.api.InvitationId;
import de.hsh.favs.sharewise.group.application.GroupService.GroupView;
import de.hsh.favs.sharewise.group.application.GroupService.PendingInvitationView;
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

/** Groups, their members and invitations to existing accounts. */
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
	InvitationResponse inviteMember(@PathVariable UUID groupId, @Valid @RequestBody InviteMemberRequest request) {
		return InvitationResponse
			.of(groups.inviteMember(new GroupId(groupId), caller(), request.identifier(), request.roleOrDefault()));
	}

	@PostMapping("/groups/{groupId}/members/{userId}/promote")
	GroupDetailResponse.MemberResponse promoteToAdmin(@PathVariable UUID groupId, @PathVariable UUID userId) {
		GroupService.MembershipView promoted = groups.promoteToAdmin(new GroupId(groupId), caller(),
				new UserId(userId));
		String name = profiles.findProfiles(Set.of(promoted.userId()))
			.stream()
			.findFirst()
			.map(UserProfile::displayName)
			.orElse(UNKNOWN_NAME);
		return new GroupDetailResponse.MemberResponse(userId, name, promoted.role());
	}

	@GetMapping("/invitations")
	List<PendingInvitationResponse> listPendingInvitations() {
		List<PendingInvitationView> pending = groups.listPendingInvitations(caller());
		Set<UserId> inviters = pending.stream().map(PendingInvitationView::invitedBy).collect(Collectors.toSet());
		Map<UUID, UserSummaryResponse> people = profiles.findProfiles(inviters)
			.stream()
			.collect(Collectors.toMap(p -> p.userId().value(), UserSummaryResponse::of, (a, b) -> a));
		return pending.stream().map(invitation -> PendingInvitationResponse.of(invitation, people)).toList();
	}

	@PostMapping("/invitations/{invitationId}/accept")
	GroupResponse acceptInvitation(@PathVariable UUID invitationId) {
		return GroupResponse.of(groups.acceptInvitation(new InvitationId(invitationId), caller()));
	}

	@PostMapping("/invitations/{invitationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void declineInvitation(@PathVariable UUID invitationId) {
		groups.declineInvitation(new InvitationId(invitationId), caller());
	}

	private UserId caller() {
		return currentUser.get().userId();
	}

}
