package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentId;
import de.hsh.favs.sharewise.settlement.application.RecordSettlementPaymentCommand;
import de.hsh.favs.sharewise.settlement.application.SettlementPaymentService;
import de.hsh.favs.sharewise.settlement.application.WaiveDebtCommand;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Record, list, confirm and reject settlement payments (R16) and waive a debt (R17). The
 * caller is always the current user; the rules live in the aggregate.
 */
@RestController
class SettlementPaymentController {

	private final SettlementPaymentService payments;

	private final SettlementResponseMapper mapper;

	private final CurrentUser currentUser;

	SettlementPaymentController(SettlementPaymentService payments, SettlementResponseMapper mapper,
			CurrentUser currentUser) {
		this.payments = payments;
		this.mapper = mapper;
		this.currentUser = currentUser;
	}

	@PostMapping("/groups/{groupId}/settlement-payments")
	@ResponseStatus(HttpStatus.CREATED)
	SettlementPaymentResponse recordSettlementPayment(@PathVariable UUID groupId,
			@Valid @RequestBody RecordSettlementPaymentRequest request) {
		var command = new RecordSettlementPaymentCommand(new GroupId(groupId), caller(),
				new UserId(request.toCreditor()), Money.eur(request.amount()), request.note());
		return mapper.toResponse(payments.record(command));
	}

	@GetMapping("/groups/{groupId}/settlement-payments")
	List<SettlementPaymentResponse> listSettlementPayments(@PathVariable UUID groupId) {
		return mapper.toResponses(payments.listPayments(new GroupId(groupId), caller()));
	}

	/**
	 * The payments waiting for the caller's answer as creditor (D10, I1), for the bell.
	 */
	@GetMapping("/me/settlement-payments")
	List<SettlementPaymentResponse> listMySettlementPayments() {
		return mapper.toResponses(payments.listAwaitingMyAnswer(caller()));
	}

	@PostMapping("/settlement-payments/{paymentId}/confirm")
	SettlementPaymentResponse confirmSettlementPayment(@PathVariable UUID paymentId) {
		return mapper.toResponse(payments.confirm(caller(), new SettlementPaymentId(paymentId)));
	}

	@PostMapping("/settlement-payments/{paymentId}/reject")
	SettlementPaymentResponse rejectSettlementPayment(@PathVariable UUID paymentId,
			@Valid @RequestBody RejectSettlementPaymentRequest request) {
		return mapper.toResponse(payments.reject(caller(), new SettlementPaymentId(paymentId), request.note()));
	}

	/**
	 * The creditor (the caller) forgives part of a debtor's debt; the answer is the
	 * WAIVED payment.
	 */
	@PostMapping("/groups/{groupId}/debt-waivers")
	@ResponseStatus(HttpStatus.CREATED)
	SettlementPaymentResponse waiveDebt(@PathVariable UUID groupId, @Valid @RequestBody WaiveDebtRequest request) {
		var command = new WaiveDebtCommand(new GroupId(groupId), caller(), new UserId(request.fromDebtor()),
				Money.eur(request.amount()), request.note());
		return mapper.toResponse(payments.waive(command));
	}

	private UserId caller() {
		return currentUser.get().userId();
	}

}
