package de.hsh.favs.sharewise.settlement.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Body of {@code POST /groups/{groupId}/debt-waivers}. {@code amount} is a decimal string
 * such as {@code "90.00"}; the reason ({@code note}) is required.
 */
record WaiveDebtRequest(@NotNull UUID fromDebtor, @NotBlank @JsonDeserialize(using = AmountText.class) String amount,
		@NotBlank @Size(max = 500) @Pattern(regexp = NoteRule.NO_NUL) String note) {

}
