package de.hsh.favs.sharewise.settlement.domain;

/**
 * Where a settlement payment stands. PENDING is the only state that can change; the other
 * three are final (R16, R17).
 */
public enum PaymentStatus {

	PENDING, CONFIRMED, REJECTED, WAIVED

}
