package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.account.api.CurrentUser;
import de.hsh.favs.sharewise.expense.api.ExpenseId;
import de.hsh.favs.sharewise.expense.application.CorrectExpenseCommand;
import de.hsh.favs.sharewise.expense.application.ExpenseService;
import de.hsh.favs.sharewise.expense.application.RecordExpenseCommand;
import de.hsh.favs.sharewise.expense.domain.ExpenseTitle;
import de.hsh.favs.sharewise.expense.domain.SplitEntry;
import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Record, correct, remove and list the expenses of a group. */
@RestController
@RequestMapping("/groups/{groupId}/expenses")
class ExpenseController {

	/** At most three digits before the point and two after, as in "33.33" or "100". */
	private static final Pattern PERCENTAGE = Pattern.compile("^-?\\d{1,3}(\\.\\d{1,2})?$");

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
				entries(request.splitStrategy(), request.participants()), request.splitStrategy());
		return ExpenseResponse.of(expenses.recordExpense(command));
	}

	@PutMapping("/{expenseId}")
	ExpenseResponse correctExpense(@PathVariable UUID groupId, @PathVariable UUID expenseId,
			@Valid @RequestBody CorrectExpenseRequest request) {
		var command = new CorrectExpenseCommand(new GroupId(groupId), new ExpenseId(expenseId), caller(),
				new ExpenseTitle(request.title()), Money.eur(request.totalAmount()), request.date(),
				new UserId(request.paidBy()), entries(request.splitStrategy(), request.participants()),
				request.splitStrategy(), request.reason());
		return ExpenseResponse.of(expenses.correctExpense(command));
	}

	@DeleteMapping("/{expenseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeExpense(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
		expenses.removeExpense(new GroupId(groupId), new ExpenseId(expenseId), caller());
	}

	private static List<SplitEntry> entries(SplitStrategy strategy,
			List<RecordExpenseRequest.Participant> participants) {
		return participants.stream()
			.map(participant -> new SplitEntry(new UserId(participant.userId()),
					typedValue(strategy, participant.value())))
			.toList();
	}

	/**
	 * The value typed for one participant: absent for EQUAL, an amount for EXACT, a
	 * percentage for PERCENTAGE. Any other shape is a malformed request (400).
	 */
	private static BigDecimal typedValue(SplitStrategy strategy, String value) {
		if (strategy == SplitStrategy.EQUAL) {
			if (value != null) {
				throw new DomainException(ErrorCode.BAD_REQUEST, "An EQUAL split takes no value per participant");
			}
			return null;
		}
		if (value == null) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "Every participant needs a value for this split");
		}
		return strategy == SplitStrategy.EXACT ? Money.eur(value).amount() : percentage(value);
	}

	private static BigDecimal percentage(String text) {
		if (!PERCENTAGE.matcher(text).matches()) {
			throw new DomainException(ErrorCode.BAD_REQUEST, "Not a valid percentage");
		}
		return new BigDecimal(text);
	}

	@GetMapping
	List<ExpenseResponse> listExpenses(@PathVariable UUID groupId) {
		return expenses.listExpenses(new GroupId(groupId), caller()).stream().map(ExpenseResponse::of).toList();
	}

	private UserId caller() {
		return currentUser.get().userId();
	}

}
