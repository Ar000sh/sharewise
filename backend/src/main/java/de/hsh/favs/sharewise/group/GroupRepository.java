package de.hsh.favs.sharewise.group;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupRepository extends JpaRepository<Group, UUID> {

	@Query("select distinct g from Group g join g.members m where m.memberId = :memberId order by g.createdAt, g.id")
	List<Group> findAllByMember(@Param("memberId") UUID memberId);

}
