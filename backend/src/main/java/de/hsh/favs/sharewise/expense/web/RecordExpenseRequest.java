package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /groups/{groupId}/expenses}. {@code totalAmount} is a decimal
 * string such as {@code "900.00"}. Length and amount rules are enforced by the value
 * objects. Each participant carries the {@code value} typed for them: an amount (EXACT),
 * a percentage (PERCENTAGE) or nothing (EQUAL); the controller checks its shape.
 */
record RecordExpenseRequest(@NotBlank String title, @NotBlank String totalAmount, @NotNull LocalDate date,
		@NotNull UUID paidBy, @NotNull @Size(min = 1) List<@NotNull @Valid Participant> participants,
		@NotNull SplitStrategy splitStrategy) {

	record Participant(@NotNull UUID userId, String value) {

	}

}
