package de.hsh.favs.sharewise.group.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.group.application.GroupArchiveService;
import de.hsh.favs.sharewise.shared.GroupId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Admin lifecycle commands and member-only immutable archives. */
@RestController
@RequestMapping("/groups/{groupId}")
class GroupLifecycleController {

	record LifecycleRequest(@NotNull @PositiveOrZero Long expectedRevision, @Size(max = 500) String reason) {
	}

	private final GroupArchiveService service;

	private final CurrentUser user;

	GroupLifecycleController(GroupArchiveService service, CurrentUser user) {
		this.service = service;
		this.user = user;
	}

	@GetMapping("/lifecycle")
	GroupArchiveService.Lifecycle get(@PathVariable UUID groupId) {
		return service.getLifecycle(new GroupId(groupId), user.get().userId());
	}

	@GetMapping("/archives")
	List<GroupArchiveService.Archive> archives(@PathVariable UUID groupId) {
		return service.listArchives(new GroupId(groupId), user.get().userId());
	}

	@PostMapping("/close")
	GroupArchiveService.Lifecycle close(@PathVariable UUID groupId, @Valid @RequestBody LifecycleRequest request) {
		return service.closeGroup(new GroupId(groupId), user.get().userId(), request.expectedRevision());
	}

	@PostMapping("/reopen")
	GroupArchiveService.Lifecycle reopen(@PathVariable UUID groupId, @Valid @RequestBody LifecycleRequest request) {
		return service.reopenGroup(new GroupId(groupId), user.get().userId(), request.expectedRevision(),
				request.reason());
	}

}
