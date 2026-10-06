package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link RefreshToken}. Implemented by a JPA
 * adapter in Phase 4.
 */
public interface RefreshTokenRepository {

	RefreshToken save(RefreshToken refreshToken);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Revokes every live token of a user, so an ADMIN reset of their password
	 * does not leave a previously issued session usable for the rest of the
	 * refresh-token TTL (plan {@code 2026-10-02-admin-reset-password.md}).
	 *
	 * <p>Already-revoked rows are left untouched — see
	 * {@link RefreshToken#revoke(Instant)} for why the first timestamp wins.
	 *
	 * @param revokedAt the timestamp to stamp, injected so the whole batch shares
	 *                  one value and the operation stays deterministic under test
	 */
	void revokeAllForUser(UUID userId, Instant revokedAt);
}