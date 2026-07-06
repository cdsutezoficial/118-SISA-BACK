package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
}
