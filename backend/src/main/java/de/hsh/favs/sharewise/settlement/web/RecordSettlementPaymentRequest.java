package de.hsh.favs.sharewise.settlement.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Body of {@code POST /groups/{groupId}/settlement-payments}. {@code amount} is a decimal
 * string such as {@code "40.00"}; the amount rules are enforced by {@code Money} and the
 * aggregate. The note is optional.
 */
record RecordSettlementPaymentRequest(@NotNull UUID toCreditor,
		@NotBlank @JsonDeserialize(using = AmountTextDeserializer.class) String amount,
		@Size(max = 500) @Pattern(regexp = NoteRule.NO_NUL) String note) {

}
