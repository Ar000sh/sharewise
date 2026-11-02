package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.settlement.application.SettlementPaymentService;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /me/settlement-payments}: the inbox of the caller (D10).
 *
 * <p>
 * Only {@code status=PENDING} and {@code role=creditor} are supported, and both are the
 * defaults. Any other value answers 400 BAD_REQUEST. The contract's enums are wider
 * (every status, role debtor); that is a known deviation, because the service answers
 * only "payments I still have to answer".
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
	List<SettlementPaymentResponse> listMySettlementPayments(@RequestParam(required = false) String status,
			@RequestParam(required = false) String role) {
		if ((status != null && !"PENDING".equals(status)) || (role != null && !"creditor".equals(role))) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "Only status=PENDING and role=creditor are supported");
		}
		return mapper.toResponses(payments.awaitingMyAnswer(currentUser.get().userId()));
	}

}
