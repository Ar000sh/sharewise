package de.hsh.favs.sharewise.account.persistence;

import de.hsh.favs.sharewise.account.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

}
