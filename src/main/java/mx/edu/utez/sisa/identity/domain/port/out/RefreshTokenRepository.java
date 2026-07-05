package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;

import java.util.Optional;

/**
 * Persistence out-port for {@link RefreshToken}. Implemented by a JPA
 * adapter in Phase 4.
 */
public interface RefreshTokenRepository {

	RefreshToken save(RefreshToken refreshToken);

	Optional<RefreshToken> findByTokenHash(String tokenHash);
}
