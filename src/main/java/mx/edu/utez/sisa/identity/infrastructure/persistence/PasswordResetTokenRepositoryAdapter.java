package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.PasswordResetToken;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PasswordResetTokenRepository} adapter delegating to
 * {@link PasswordResetTokenJpaRepository}.
 */
@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

	private final PasswordResetTokenJpaRepository jpaRepository;

	public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PasswordResetToken save(PasswordResetToken passwordResetToken) {
		return jpaRepository.save(passwordResetToken);
	}

	@Override
	public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
		return jpaRepository.findByTokenHash(tokenHash);
	}

	@Override
	public List<PasswordResetToken> findByUserId(UUID userId) {
		return jpaRepository.findByUserId(userId);
	}
}