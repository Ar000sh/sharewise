package de.hsh.favs.sharewise.expense.domain;

import de.hsh.favs.sharewise.shared.DomainException;
import de.hsh.favs.sharewise.shared.ErrorCode;
import de.hsh.favs.sharewise.shared.GroupId;
import de.hsh.favs.sharewise.shared.MemberId;
import de.hsh.favs.sharewise.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** A recorded expense with its equal shares. Immutable once created. */
public final class Expense {

	private final UUID id;
	private final GroupId groupId;
	private final String title;
	private final Money amount;
	private final LocalDate date;
	private final MemberId payerId;
	private final List<Share> shares;
	private final Instant createdAt;

	private Expense(UUID id, GroupId groupId, String title, Money amount, LocalDate date, MemberId payerId,
			List<Share> shares, Instant createdAt) {
		this.id = id;
		this.groupId = groupId;
		this.title = title;
		this.amount = amount;
		this.date = date;
		this.payerId = payerId;
		this.shares = shares;
		this.createdAt = createdAt;
	}

	public static Expense record(GroupId groupId, String title, Money amount, LocalDate date, MemberId payer,
			List<MemberId> participants) {
		if (!amount.isPositive()) {
			throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
		}
		if (participants.isEmpty()) {
			throw new DomainException(ErrorCode.NO_PARTICIPANTS, "At least one participant is required");
		}
		if (new HashSet<>(participants).size() != participants.size()) {
			throw new DomainException(ErrorCode.NO_PARTICIPANTS, "Participants must be distinct");
		}
		return new Expense(UUID.randomUUID(), groupId, title, amount, date, payer,
				EqualSplit.split(amount, participants), Instant.now());
	}

	/** Rebuilds a stored expense without re-validating. */
	public static Expense restore(UUID id, GroupId groupId, String title, Money amount, LocalDate date,
			MemberId payer, List<Share> shares, Instant createdAt) {
		return new Expense(id, groupId, title, amount, date, payer, List.copyOf(shares), createdAt);
	}

	public UUID id() { return id; }

	public GroupId groupId() { return groupId; }

	public String title() { return title; }

	public Money amount() { return amount; }

	public LocalDate date() { return date; }

	public MemberId payerId() { return payerId; }

	public List<Share> shares() { return shares; }

	public Instant createdAt() { return createdAt; }

}
