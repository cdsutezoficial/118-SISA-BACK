package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link RefreshTokenRepositoryAdapter}.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Bulk revoke. A JPQL update rather than a load-and-mutate loop: a busy
	 * account can hold one row per login (tokens are never rotated or pruned
	 * today), so the reset must not drag every row through the persistence
	 * context just to flip one column.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :revokedAt where t.userId = :userId and t.revokedAt is null")
	int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}