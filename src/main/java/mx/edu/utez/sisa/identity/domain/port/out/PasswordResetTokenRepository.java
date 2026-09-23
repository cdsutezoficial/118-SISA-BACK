package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.identity.domain.model.PasswordResetToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PasswordResetToken}. Implemented by a JPA
 * adapter. {@code findByUserId} backs the one-active-token-at-a-time invariant
 * (01-identidad.md: "Al generar un nuevo token se invalidan los anteriores del
 * mismo usuario"), so {@code RequestPasswordResetUseCase} can invalidate the
 * previous ones before persisting a fresh token.
 */
public interface PasswordResetTokenRepository {

	PasswordResetToken save(PasswordResetToken passwordResetToken);

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	List<PasswordResetToken> findByUserId(UUID userId);
}