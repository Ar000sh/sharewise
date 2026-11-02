package de.hsh.favs.sharewise.settlement.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /settlement-payments/{paymentId}/reject}: the reason is required.
 */
record RejectSettlementPaymentRequest(@NotBlank @Size(max = 500) String note) {

}
