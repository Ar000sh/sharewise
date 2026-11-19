package de.hsh.favs.sharewise.settlement.api;

import java.util.UUID;

/** Identity of a settlement payment. Lives in {@code api} because events carry it. */
public record SettlementPaymentId(UUID value) {

}
