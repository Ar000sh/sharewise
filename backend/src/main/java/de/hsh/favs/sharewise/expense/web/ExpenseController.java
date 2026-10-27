package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.expense.application.ExpenseService;
import de.hsh.favs.sharewise.expense.application.RecordExpenseCommand;
import de.hsh.favs.sharewise.expense.domain.ExpenseTitle;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Record and list the expenses of a group. */
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
	ExpenseResponse recordExpense(@PathVariable UUID groupId, @Valid @RequestBody RecordExpenseRequest request) {
		var command = new RecordExpenseCommand(new GroupId(groupId), caller(), new ExpenseTitle(request.title()),
				Money.eur(request.totalAmount()), request.date(), new UserId(request.paidBy()),
				request.participantIds().stream().map(UserId::new).toList(), request.splitStrategy());
		return ExpenseResponse.of(expenses.recordExpense(command));
	}

	@GetMapping
	List<ExpenseResponse> listExpenses(@PathVariable UUID groupId) {
		return expenses.listExpenses(new GroupId(groupId), caller()).stream().map(ExpenseResponse::of).toList();
	}

	private UserId caller() {
		return currentUser.get().userId();
	}

}
