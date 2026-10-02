package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link RefreshTokenRepository} adapter delegating to
 * {@link RefreshTokenJpaRepository}.
 */
@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

	private final RefreshTokenJpaRepository jpaRepository;

	public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public RefreshToken save(RefreshToken refreshToken) {
		return jpaRepository.save(refreshToken);
	}

	@Override
	public Optional<RefreshToken> findByTokenHash(String tokenHash) {
		return jpaRepository.findByTokenHash(tokenHash);
	}

	/**
	 * {@link Propagation#REQUIRES_NEW}: the caller's transaction is already
	 * mid-flight in a use case that has just mutated the {@code User} row, and
	 * the bulk update must not be folded into it — if the caller rolls back
	 * afterwards, a session revoked by a password reset that never landed would
	 * have been killed for no reason. Also {@code @Transactional} because the
	 * Spring Data {@code @Modifying} query requires an active transaction, which
	 * the caller cannot be relied upon to provide.
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void revokeAllForUser(UUID userId, Instant revokedAt) {
		jpaRepository.revokeAllForUser(userId, revokedAt);
	}
}