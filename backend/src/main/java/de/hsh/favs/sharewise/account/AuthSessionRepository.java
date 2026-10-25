package de.hsh.favs.sharewise.account;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

}
