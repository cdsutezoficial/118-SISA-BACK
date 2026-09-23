package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.PasswordResetToken;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ResetPasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.InvalidPasswordResetTokenException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Completes the "forgot my password" flow (01-identidad.md —
 * ResetPasswordUseCase): resolves the token by hash and rejects the request —
 * leaving the account password untouched — when the token is unknown, already
 * used, or past its TTL. On success it hashes the new password, applies it via
 * {@link User#changePassword} (which also lifts the {@code mustChangePassword}
 * flag), and marks the token consumed so it cannot be replayed.
 */
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;

	public ResetPasswordUseCaseImpl(PasswordResetTokenRepository passwordResetTokenRepository,
			UserRepository userRepository, PasswordHasher passwordHasher) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
	}

	@Override
	@Transactional
	public void reset(ResetPasswordCommand command) {
		String tokenHash = TokenHashing.sha256(command.token());
		PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new InvalidPasswordResetTokenException(
						"El enlace de restablecimiento no es válido."));

		if (token.isUsed()) {
			throw new InvalidPasswordResetTokenException(
					"El enlace de restablecimiento ya fue utilizado.");
		}
		if (token.getExpiresAt().isBefore(Instant.now())) {
			throw new InvalidPasswordResetTokenException(
					"El enlace de restablecimiento ha expirado.");
		}

		User user = userRepository.findById(token.getUserId())
				.orElseThrow(() -> new InvalidPasswordResetTokenException(
						"El enlace de restablecimiento no es válido."));

		String newPasswordHash = passwordHasher.hash(command.newPassword());
		user.changePassword(newPasswordHash);
		userRepository.save(user);

		token.markUsed();
		passwordResetTokenRepository.save(token);
	}
}