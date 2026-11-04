package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.settlement.application.SettlementPlanService;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /groups/{groupId}/settlement-plan}: who pays whom to settle the group (R15),
 * with the amount already reserved on each pair and the amount payable now.
 */
@RestController
class SettlementPlanController {

	private final SettlementPlanService plans;

	private final SettlementResponseMapper mapper;

	private final CurrentUser currentUser;

	SettlementPlanController(SettlementPlanService plans, SettlementResponseMapper mapper, CurrentUser currentUser) {
		this.plans = plans;
		this.mapper = mapper;
		this.currentUser = currentUser;
	}

	@GetMapping("/groups/{groupId}/settlement-plan")
	List<SettlementPlanEntryResponse> getSettlementPlan(@PathVariable UUID groupId) {
		return mapper.toPlanResponses(plans.planFor(new GroupId(groupId), currentUser.get().userId()));
	}

}
