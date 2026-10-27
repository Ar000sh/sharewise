package de.hsh.favs.sharewise.expense.web;

import de.hsh.favs.sharewise.shared.UserId;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hsh.favs.sharewise.expense.ExpenseIntegrationTest;
import de.hsh.favs.sharewise.expense.StubCurrentUser;
import de.hsh.favs.sharewise.expense.StubGroupMembership;
import de.hsh.favs.sharewise.group.api.GroupStatus;
import de.hsh.favs.sharewise.shared.GroupId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract only: status codes, JSON shape and problem+json codes. The business
 * rules are covered by ExpenseServiceTest and ExpenseTest.
 */
@ExpenseIntegrationTest
class ExpenseControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	StubGroupMembership groups;

	@Autowired
	StubCurrentUser currentUser;

	UUID group;

	UUID alice = StubCurrentUser.ALICE.userId().value();

	UUID bob = UUID.randomUUID();

	UUID carol = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		groups.reset();
		currentUser.reset();
		group = UUID.randomUUID();
		groups.group(new GroupId(group), GroupStatus.OPEN, member(alice), member(bob), member(carol));
	}

	static UserId member(UUID id) {
		return new UserId(id);
	}

	ExpenseRequests request() {
		return new ExpenseRequests(mvc).inGroup(group);
	}

	@Test
	void postReturns201AndTheContractShape() throws Exception {
		// when
		var response = request().withAmountCents(1_000).paidBy(alice).splitBetween(alice, bob, carol).record();

		// then
		response.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.title").value("Hotel"))
			.andExpect(jsonPath("$.amountCents").value(1_000))
			.andExpect(jsonPath("$.date").value("2027-06-01"))
			.andExpect(jsonPath("$.payerId").value(alice.toString()))
			.andExpect(jsonPath("$.shares.length()").value(3))
			.andExpect(jsonPath("$.shares[0].memberId").value(alice.toString()))
			.andExpect(jsonPath("$.shares[0].shareCents").value(334))
			.andExpect(jsonPath("$.shares[1].memberId").value(bob.toString()))
			.andExpect(jsonPath("$.shares[1].shareCents").value(333))
			.andExpect(jsonPath("$.shares[2].shareCents").value(333));
	}

	@Test
	void splitPercentageIs422SplitNotSupported() throws Exception {
		// when
		var response = request().splitBetween(alice).withSplit("PERCENTAGE").record();

		// then
		response.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SPLIT_NOT_SUPPORTED"));
	}

	@Test
	void unknownSplitValueIs400() throws Exception {
		request().splitBetween(alice)
			.withSplit("WEIRD")
			.record()
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void blankTitleIs400() throws Exception {
		request().withTitle("")
			.splitBetween(alice)
			.record()
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void listIsNewestFirstByDateThenCreationTimeWithShares() throws Exception {
		// given
		request().withTitle("Old")
			.onDate("2027-06-01")
			.splitBetween(alice, bob)
			.record()
			.andExpect(status().isCreated());
		request().withTitle("Newest")
			.onDate("2027-06-03")
			.paidBy(bob)
			.withAmountCents(200)
			.splitBetween(alice, bob)
			.record()
			.andExpect(status().isCreated());
		request().withTitle("SameDayLater")
			.onDate("2027-06-01")
			.splitBetween(alice)
			.record()
			.andExpect(status().isCreated());

		// when
		var response = request().list();

		// then
		response.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].title").value("Newest"))
			.andExpect(jsonPath("$[0].shares.length()").value(2))
			.andExpect(jsonPath("$[0].shares[0].shareCents").value(100))
			.andExpect(jsonPath("$[1].title").value("SameDayLater"))
			.andExpect(jsonPath("$[2].title").value("Old"));
	}

	@Test
	void invalidUuidIs400() throws Exception {
		request().inGroup("not-a-uuid")
			.splitBetween(alice)
			.record()
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		request().paidBy("nope")
			.splitBetween(alice)
			.record()
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		request().inGroup("not-a-uuid")
			.list()
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void callerNotMemberIs403() throws Exception {
		// given
		groups.group(new GroupId(group), GroupStatus.OPEN, member(bob), member(carol));

		// when / then
		request().paidBy(bob)
			.splitBetween(bob)
			.record()
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
		request().list().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
	}

	@Test
	void unknownGroupIs404() throws Exception {
		request().inGroup(UUID.randomUUID())
			.list()
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

}
