package de.hsh.favs.sharewise.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hsh.favs.sharewise.group.api.GroupMembership;
import de.hsh.favs.sharewise.settlement.application.SettlementPlanService.PlanEntry;
import de.hsh.favs.sharewise.settlement.domain.SettlementPaymentFixture;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository;
import de.hsh.favs.sharewise.settlement.persistence.GroupLedgerRepository.UserTotal;
import de.hsh.favs.sharewise.settlement.persistence.SettlementPaymentRepository;
import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.Money;
import de.hsh.favs.sharewise.shared.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Orchestration of the plan read model: the caller is checked first, the plan comes from
 * the ledger totals, and each edge carries what is reserved on exactly its pair.
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

	@InjectMocks
	SettlementPlanService service;

	@Test
	void planForOwesEntriesWithTheReservedAmountOfTheirPair() {
		// given bob owes alice 90.00 and has 40.00 reserved on that pair
		when(ledgers.totals(GROUP.value())).thenReturn(List.of(total(ALICE, "90.00"), total(BOB, "-90.00")));
		when(payments.reserved(GROUP.value(), BOB.value(), ALICE.value())).thenReturn(new BigDecimal("40.00"));

		// when
		List<PlanEntry> plan = service.planFor(GROUP, ALICE);

		// then
		assertThat(plan).containsExactly(new PlanEntry(BOB, ALICE, Money.eur("90.00"), Money.eur("40.00")));
	}

	@Test
	void anEmptyLedgerGivesAnEmptyPlan() {
		when(ledgers.totals(GROUP.value())).thenReturn(List.of());

		assertThat(service.planFor(GROUP, ALICE)).isEmpty();
	}

	@Test
	void aCallerWhoIsNotAMemberIsRefusedBeforeAnythingIsRead() {
		// given
		doThrow(new DomainException(ErrorCode.NOT_A_MEMBER, "no")).when(groups).requireMember(GROUP, BOB);

		// when / then
		assertThatThrownBy(() -> service.planFor(GROUP, BOB)).isInstanceOfSatisfying(DomainException.class,
				e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_A_MEMBER));
		verifyNoInteractions(ledgers, payments);
	}

	private static UserTotal total(UserId user, String amount) {
		return new UserTotal() {
			@Override
			public UUID getUserId() {
				return user.value();
			}

			@Override
			public BigDecimal getAmount() {
				return new BigDecimal(amount);
			}
		};
	}

}
