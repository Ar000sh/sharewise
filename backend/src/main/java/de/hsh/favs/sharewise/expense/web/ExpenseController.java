package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.expense.application.ExpenseService;
import de.hsh.favs.sharewise.expense.application.RecordExpenseCommand;
import de.hsh.favs.sharewise.shared.GroupId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
class ExpenseController {

	private final ExpenseService expenses;

	private final CurrentUser currentUser;

	ExpenseController(ExpenseService expenses, CurrentUser currentUser) {
		this.expenses = expenses;
		this.currentUser = currentUser;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ExpenseResponse record(@PathVariable UUID groupId, @Valid @RequestBody ExpenseRequest request) {
		var command = new RecordExpenseCommand(new GroupId(groupId), caller(), request.title(), request.amountCents(),
				request.date(), new UserId(request.payerId()),
				request.participantIds().stream().map(UserId::new).toList(), request.split());
		return ExpenseResponse.from(expenses.record(command));
	}

	@GetMapping
	List<ExpenseResponse> list(@PathVariable UUID groupId) {
		return expenses.list(new GroupId(groupId), caller()).stream().map(ExpenseResponse::from).toList();
	}

	private UserId caller() {
		return currentUser.get().id();
	}

}
