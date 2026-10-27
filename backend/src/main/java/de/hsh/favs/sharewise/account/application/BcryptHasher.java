package de.hsh.favs.sharewise.account.application;

import de.hsh.favs.sharewise.account.domain.Hasher;
import de.hsh.favs.sharewise.account.domain.PasswordHash;
import de.hsh.favs.sharewise.account.domain.RawPassword;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link Hasher} with BCrypt for passwords and SHA-256 for the (already random) session
 * tokens.
 */
@Component
class BcryptHasher implements Hasher {

	private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

	@Override
	public PasswordHash hashPassword(RawPassword password) {
		return new PasswordHash(passwords.encode(password.value()));
	}

	@Override
	public boolean matchesPassword(RawPassword password, PasswordHash hash) {
		return passwords.matches(password.value(), hash.value());
	}

	@Override
	public String hashToken(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Override
	public boolean matchesToken(String rawToken, String tokenHash) {
		return MessageDigest.isEqual(hashToken(rawToken).getBytes(StandardCharsets.UTF_8),
				tokenHash.getBytes(StandardCharsets.UTF_8));
	}

}
