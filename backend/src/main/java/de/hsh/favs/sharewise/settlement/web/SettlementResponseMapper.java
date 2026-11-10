package de.hsh.favs.sharewise.settlement.web;

import de.hsh.favs.sharewise.account.api.UserProfile;
import de.hsh.favs.sharewise.account.api.UserProfiles;
import de.hsh.favs.sharewise.settlement.application.SettlementPlanService.PlanEntry;
import de.hsh.favs.sharewise.settlement.domain.SettlementPayment;
import de.hsh.favs.sharewise.shared.UserId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Turns settlement payments and plan entries into responses. The one place that looks up
 * display names: a single batched lookup per request, "Unknown user" for an id without an
 * account (as in the balances).
 */
@Component
class SettlementResponseMapper {

	private static final String UNKNOWN_NAME = "Unknown user";

	private final UserProfiles profiles;

	SettlementResponseMapper(UserProfiles profiles) {
		this.profiles = profiles;
	}

	SettlementPaymentResponse toResponse(SettlementPayment payment) {
		return toResponses(List.of(payment)).get(0);
	}

	List<SettlementPaymentResponse> toResponses(Collection<SettlementPayment> payments) {
		Set<UserId> users = new HashSet<>();
		payments.forEach(payment -> {
			users.add(payment.fromDebtor());
			users.add(payment.toCreditor());
		});
		Map<UserId, String> names = namesOf(users);
		return payments.stream()
			.map(payment -> new SettlementPaymentResponse(payment.paymentId().value(), payment.groupId().value(),
					payment.fromDebtor().value(), name(names, payment.fromDebtor()), payment.toCreditor().value(),
					name(names, payment.toCreditor()), payment.amount().amount().toPlainString(), payment.status(),
					payment.note(), payment.createdAt(), payment.resolvedAt()))
			.toList();
	}

	List<SettlementPlanEntryResponse> toPlanResponses(List<PlanEntry> entries) {
		Set<UserId> users = new HashSet<>();
		entries.forEach(entry -> {
			users.add(entry.fromDebtor());
			users.add(entry.toCreditor());
		});
		Map<UserId, String> names = namesOf(users);
		return entries.stream()
			.map(entry -> new SettlementPlanEntryResponse(entry.fromDebtor().value(), name(names, entry.fromDebtor()),
					entry.toCreditor().value(), name(names, entry.toCreditor()),
					entry.amount().amount().toPlainString(), entry.pendingAmount().amount().toPlainString(),
					entry.payableAmount().amount().toPlainString()))
			.toList();
	}

	private Map<UserId, String> namesOf(Set<UserId> users) {
		if (users.isEmpty()) {
			return Map.of();
		}
		return profiles.findProfiles(users)
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, UserProfile::displayName, (a, b) -> a));
	}

	private static String name(Map<UserId, String> names, UserId user) {
		return names.getOrDefault(user, UNKNOWN_NAME);
	}

}
