package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.settlement.application.SettlementPaymentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /me/settlement-payments}: the payments waiting for the caller's answer as
 * creditor (D10, I1), a query for the bell; it takes no parameters.
 */
@RestController
class MySettlementPaymentsController {

	private final SettlementPaymentService payments;

	private final SettlementResponseMapper mapper;

	private final CurrentUser currentUser;

	MySettlementPaymentsController(SettlementPaymentService payments, SettlementResponseMapper mapper,
			CurrentUser currentUser) {
		this.payments = payments;
		this.mapper = mapper;
		this.currentUser = currentUser;
	}

	@GetMapping("/me/settlement-payments")
	List<SettlementPaymentResponse> listMySettlementPayments() {
		return mapper.toResponses(payments.awaitingMyAnswer(currentUser.get().userId()));
	}

}
