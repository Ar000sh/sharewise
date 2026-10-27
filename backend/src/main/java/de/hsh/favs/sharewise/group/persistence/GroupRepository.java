package de.hsh.favs.sharewise.group.persistence;

import de.hsh.favs.sharewise.group.domain.Group;
import jakarta.persistence.LockModeType;
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

	@Query("select g.id from Group g join g.invitations i where i.token = :token")
	Optional<UUID> findIdByInvitationToken(@Param("token") String token);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Group g where g.id = :id")
	Optional<Group> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select distinct g from Group g join g.memberships m
			where m.userId = :userId and m.status = de.hsh.favs.sharewise.group.domain.MembershipStatus.ACTIVE
			order by g.createdAt, g.id""")
	List<Group> findAllByActiveMember(@Param("userId") UUID userId);

}
