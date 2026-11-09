package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.application.SettlementPlanService.PlanEntry;
import de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture;
import de.hsh.favs.sharewise.settlement.api.MemberBalance;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration of the plan read model: the caller is checked first, the plan comes from
 * the ledger balances, and each edge carries what is reserved on exactly its pair and
 * what the open debt policy would accept on it.
 */
@ExtendWith(MockitoExtension.class)
class SettlementPlanServiceTest {

	static final GroupId GROUP = SettlementPaymentFixture.GROUP;

	static final UserId ALICE = SettlementPaymentFixture.ALICE;

	static final UserId BOB = SettlementPaymentFixture.BOB;

	@Mock
	GroupMembership groups;

	@Mock
	GroupLedgerRepository ledgers;

	@Mock
	SettlementPaymentRepository payments;

	SettlementPlanService service;

	@BeforeEach
	void setUp() {
		// the real policy over mocked repositories: the plan must use its formula
		service = new SettlementPlanService(groups, new OpenDebtPolicy(ledgers, payments));
	}

	@Test
	void planGivesEachEntryTheReservedAndThePayableAmountOfItsPair() {
		// given bob owes alice 90.00 and has 40.00 reserved on that pair
		when(ledgers.balances(GROUP)).thenReturn(List.of(memberBalance(ALICE, "90.00"), memberBalance(BOB, "-90.00")));
		when(payments.reserved(GROUP.value(), BOB.value(), ALICE.value())).thenReturn(new BigDecimal("40.00"));
		when(payments.reserved(GROUP.value(), BOB.value(), null)).thenReturn(new BigDecimal("40.00"));
		when(payments.reserved(GROUP.value(), null, ALICE.value())).thenReturn(new BigDecimal("40.00"));

		// when
		List<PlanEntry> plan = service.listPlanEntries(GROUP, ALICE);

		// then an ordinary edge can still be paid by amount minus pendingAmount
		assertThat(plan)
			.containsExactly(new PlanEntry(BOB, ALICE, Money.eur("90.00"), Money.eur("40.00"), Money.eur("50.00")));
	}

	@Test
	void anEmptyLedgerGivesAnEmptyPlan() {
		when(ledgers.balances(GROUP)).thenReturn(List.of());

		assertThat(service.listPlanEntries(GROUP, ALICE)).isEmpty();
	}

	@Test
	void aCallerWhoIsNotAMemberIsRefusedBeforeAnythingIsRead() {
		// given
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(groups).requireMember(GROUP, BOB);

		// when / then
		assertThatThrownBy(() -> service.listPlanEntries(GROUP, BOB)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		verifyNoInteractions(ledgers, payments);
	}

	private static MemberBalance memberBalance(UserId user, String amount) {
		return new MemberBalance(user, Money.eur(amount));
	}

}
