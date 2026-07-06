package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link RefreshTokenRepositoryAdapter}.
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);
}
