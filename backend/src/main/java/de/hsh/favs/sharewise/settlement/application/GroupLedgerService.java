package de.hsh.favs.sharewise.settlement.application;

import de.hsh.favs.sharewise.expense.api.ExpenseRecorded;
import de.hsh.favs.sharewise.settlement.api.DebtWaived;
import de.hsh.favs.sharewise.settlement.api.SettlementPaymentConfirmed;
import de.hsh.favs.sharewise.settlement.domain.GroupLedger;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.UserId;
import de.hsh.favs.sharewise.shared.events.AllBalancesSettled;
import de.hsh.favs.sharewise.shared.events.MemberBalanceStatusChanged;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the ledger aggregate: book what the other contexts announce, and tell the
 * group whose balance became or stopped being zero after every booking, expenses included
 * (R11, D12).
 */
@Service
public class GroupLedgerService {

	private final GroupLedgerRepository ledgers;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	GroupLedgerService(GroupLedgerRepository ledgers, ApplicationEventPublisher events, Clock clock) {
		this.ledgers = ledgers;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Books an expense in its group's ledger, creating the ledger on the first event.
	 * Applying the same event twice books it once: the event may be delivered more than
	 * once.
	 */
	@Transactional
	public void bookExpense(ExpenseRecorded expense) {
		book(expense.groupId(), expense.expenseId().value(), (ledger, now) -> ledger.appendExpenseEntry(expense, now));
	}

	/**
	 * Books a confirmed settlement payment in its group's ledger (R14, R16, design D8),
	 * creating the ledger if needed. Idempotent: the entries reference the payment id, so
	 * a second delivery finds them and books nothing.
	 */
	@Transactional
	public void bookSettlementPayment(SettlementPaymentConfirmed payment) {
		book(payment.groupId(), payment.paymentId().value(),
				(ledger, now) -> ledger.appendSettlementPayment(payment, now));
	}

	/**
	 * Books a waived debt in its group's ledger (R14, R17, design D8); idempotent like
	 * {@link #bookSettlementPayment}.
	 */
	@Transactional
	public void bookDebtWaiver(DebtWaived waiver) {
		book(waiver.groupId(), waiver.paymentId().value(), (ledger, now) -> ledger.appendDebtWaiver(waiver, now));
	}

	/**
	 * Lock, check, append, save; then compare every person's net balance before and after
	 * and publish the zero-state flips. A redelivery books nothing and so publishes
	 * nothing.
	 */
	private void book(GroupId groupId, UUID referenceId, BiConsumer<GroupLedger, Instant> append) {
		var group = groupId.value();
		ledgers.createIfAbsent(group);
		GroupLedger ledger = ledgers.findForUpdate(group).orElseThrow();
		if (ledgers.isBooked(group, referenceId)) {
			return;
		}
		Map<UUID, BigDecimal> balancesBefore = netBalancesByPerson(group);
		Instant now = clock.instant();
		append.accept(ledger, now);
		ledgers.save(ledger);
		// the balances query must see the entries just appended
		ledgers.flush();
		publishZeroStateFlips(groupId, balancesBefore, netBalancesByPerson(group), now);
	}

	/**
	 * One {@link MemberBalanceStatusChanged} per person whose zero-state flipped (a
	 * person without entries counts as zero), and {@link AllBalancesSettled} when
	 * everybody is zero now and somebody was not before. Sorted by user id, so the order
	 * is stable.
	 */
	private void publishZeroStateFlips(GroupId groupId, Map<UUID, BigDecimal> balancesBefore,
			Map<UUID, BigDecimal> balancesAfter, Instant now) {
		var people = new TreeSet<UUID>(balancesBefore.keySet());
		people.addAll(balancesAfter.keySet());
		for (UUID person : people) {
			boolean wasZero = isZero(balancesBefore.get(person));
			boolean isZeroNow = isZero(balancesAfter.get(person));
			if (wasZero != isZeroNow) {
				events.publishEvent(new MemberBalanceStatusChanged(groupId, new UserId(person), isZeroNow, now));
			}
		}
		boolean everybodyZeroNow = balancesAfter.values().stream().allMatch(GroupLedgerService::isZero);
		boolean somebodyWasNot = !balancesBefore.values().stream().allMatch(GroupLedgerService::isZero);
		if (everybodyZeroNow && somebodyWasNot) {
			events.publishEvent(new AllBalancesSettled(groupId, now));
		}
	}

	private Map<UUID, BigDecimal> netBalancesByPerson(UUID group) {
		return ledgers.balances(new GroupId(group))
			.stream()
			.collect(Collectors.toMap(balance -> balance.userId().value(), balance -> balance.netBalance().amount(),
					BigDecimal::add, HashMap::new));
	}

	private static boolean isZero(BigDecimal amount) {
		return amount == null || amount.signum() == 0;
	}

}
