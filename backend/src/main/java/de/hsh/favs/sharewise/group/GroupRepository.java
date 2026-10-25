package de.hsh.favs.sharewise.group;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupRepository extends JpaRepository<Group, UUID> {

	@Query("select g.id from Group g join g.invitations i where i.token = :token")
	Optional<UUID> findIdByInvitationToken(@Param("token") String token);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from Group g where g.id = :id")
	Optional<Group> findByIdForUpdate(@Param("id") UUID id);

	@Query("select distinct g from Group g join g.members m where m.memberId = :memberId order by g.createdAt, g.id")
	List<Group> findAllByMember(@Param("memberId") UUID memberId);

}
