package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.settlement.application.SettlementPaymentService;
import de.hsh.favs.sharewise.settlement.application.WaiveDebtCommand;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /groups/{groupId}/debt-waivers}: the creditor (the caller) forgives part of
 * a debtor's debt; the answer is the WAIVED settlement payment (R17).
 */
@RestController
class DebtWaiverController {

	private final SettlementPaymentService payments;

	private final SettlementResponseMapper mapper;

	private final CurrentUser currentUser;

	DebtWaiverController(SettlementPaymentService payments, SettlementResponseMapper mapper, CurrentUser currentUser) {
		this.payments = payments;
		this.mapper = mapper;
		this.currentUser = currentUser;
	}

	@PostMapping("/groups/{groupId}/debt-waivers")
	@ResponseStatus(HttpStatus.CREATED)
	SettlementPaymentResponse waiveDebt(@PathVariable UUID groupId, @Valid @RequestBody WaiveDebtRequest request) {
		var command = new WaiveDebtCommand(new GroupId(groupId), currentUser.get().userId(),
				new UserId(request.fromDebtor()), Money.eur(request.amount()), request.note());
		return mapper.toResponse(payments.waive(command));
	}

}
