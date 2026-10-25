package de.hsh.favs.sharewise.group;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	Optional<Invitation> findByToken(String token);

}
