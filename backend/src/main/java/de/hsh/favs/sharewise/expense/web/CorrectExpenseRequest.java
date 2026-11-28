package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.domain.SplitStrategy;
import de.hsh.favs.sharewise.expense.web.RecordExpenseRequest.Participant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PUT /groups/{groupId}/expenses/{expenseId}}: the new version, shaped
 * like {@link RecordExpenseRequest}, plus an optional reason shown in the history.
 */
record CorrectExpenseRequest(@NotBlank String title, @NotBlank String totalAmount, @NotNull LocalDate date,
		@NotNull UUID paidBy, @NotNull @Size(min = 1) List<@NotNull @Valid Participant> participants,
		@NotNull SplitStrategy splitStrategy, @Size(max = 200) String reason) {

}
