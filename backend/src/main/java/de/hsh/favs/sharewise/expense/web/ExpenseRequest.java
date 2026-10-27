package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.expense.application.SplitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record ExpenseRequest(@NotBlank @Size(max = 200) String title, @NotNull Long amountCents, @NotNull LocalDate date,
		@NotNull UUID payerId, @NotNull List<@NotNull UUID> participantIds, @NotNull SplitType split) {
}
