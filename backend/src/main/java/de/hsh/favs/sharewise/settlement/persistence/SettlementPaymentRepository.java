package de.hsh.favs.sharewise.settlement.persistence;

import de.hsh.favs.sharewise.settlement.domain.PaymentStatus;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Stores settlement payments and sums the part of the open debt they reserve. */
public interface SettlementPaymentRepository extends JpaRepository<SettlementPayment, UUID> {

	List<SettlementPayment> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

	List<SettlementPayment> findByToCreditorAndStatusOrderByCreatedAtDesc(UUID toCreditor, PaymentStatus status);

	/**
	 * The amount of this group's payments that is reserved and not yet in the balances:
	 * PENDING ones, and CONFIRMED or WAIVED ones the ledger has not booked yet, that is
	 * with no ledger entry referencing the payment (D5). A null party means "anyone".
	 * Zero when nothing matches.
	 * <p>
	 * The deliberate exception to "one aggregate per repository": the query reads
	 * {@code LedgerEntry}, a child of the {@code GroupLedger} aggregate in the same
	 * module, to tell whether a confirmed or waived payment is booked yet (D5). Splitting
	 * it into two queries would cost more than the exception (settlements inbox design,
	 * section 4).
	 */
	@Query("select coalesce(sum(p.amount), 0) from SettlementPayment p where p.groupId = :groupId"
			+ " and (:fromDebtor is null or p.fromDebtor = :fromDebtor)"
			+ " and (:toCreditor is null or p.toCreditor = :toCreditor)"
			+ " and (p.status = de.hsh.favs.sharewise.settlement.domain.PaymentStatus.PENDING"
			+ " or (p.status in (de.hsh.favs.sharewise.settlement.domain.PaymentStatus.CONFIRMED,"
			+ " de.hsh.favs.sharewise.settlement.domain.PaymentStatus.WAIVED)"
			+ " and not exists (select e from LedgerEntry e where e.referenceId = p.id)))")
	BigDecimal reserved(@Param("groupId") UUID groupId, @Param("fromDebtor") UUID fromDebtor,
			@Param("toCreditor") UUID toCreditor);

}
