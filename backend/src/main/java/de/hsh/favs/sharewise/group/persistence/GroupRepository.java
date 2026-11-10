package de.hsh.favs.sharewise.group.persistence;

import de.hsh.favs.sharewise.group.domain.Group;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, UUID> {

	/**
	 * Answered by the database, so callers outside a transaction never touch lazy state.
	 */
	@Query("""
			select count(m) > 0 from Group g join g.memberships m
			where g.id = :groupId and m.userId = :userId and m.status = de.hsh.favs.sharewise.group.domain.MembershipStatus.ACTIVE""")
	boolean existsActiveMember(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

	@Query("select g.id from Group g join g.invitations i where i.id = :invitationId")
	Optional<UUID> findIdByInvitationId(@Param("invitationId") UUID invitationId);

	/**
	 * The user's open invitations (stored PENDING, not expired at {@code now}; the expiry
	 * instant itself still counts as open), newest first, as plain rows in one query. The
	 * same rule as {@code Invitation.statusAt}, asked of the database so a list does not
	 * load whole groups.
	 */
	@Query("""
			select new de.hsh.favs.sharewise.group.persistence.PendingInvitationRow(
				i.id, g.id, g.name, i.createdBy, i.role, i.expiresAt)
			from Group g join g.invitations i
			where i.invitee = :userId
			and i.status = de.hsh.favs.sharewise.group.domain.InvitationStatus.PENDING
			and i.expiresAt >= :now
			order by i.createdAt desc, i.id desc""")
	List<PendingInvitationRow> findPendingInvitationsFor(@Param("userId") UUID userId, @Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Group g where g.id = :id")
	Optional<Group> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select distinct g from Group g join g.memberships m
			where m.userId = :userId and m.status = de.hsh.favs.sharewise.group.domain.MembershipStatus.ACTIVE
			order by g.createdAt, g.id""")
	List<Group> findAllByActiveMember(@Param("userId") UUID userId);

}
