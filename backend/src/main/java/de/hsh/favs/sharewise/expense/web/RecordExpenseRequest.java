package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /groups/{groupId}/expenses}. {@code totalAmount} is a decimal
 * string such as {@code "900.00"}. Length and amount rules are enforced by the value
 * objects.
 */
record RecordExpenseRequest(@NotBlank String title, @NotBlank String totalAmount, @NotNull LocalDate date,
		@NotNull UUID paidBy, @NotNull List<@NotNull UUID> participantIds, @NotNull SplitStrategy splitStrategy) {

}
