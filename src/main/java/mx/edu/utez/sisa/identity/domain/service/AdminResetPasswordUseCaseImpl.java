package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.AdminResetPasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.NotificationPort;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.TemporaryPasswordGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * ADMIN-side password recovery (plan
 * {@code 2026-10-02-admin-reset-password.md}): generates a temporary password,
 * applies it through {@link User#forceTemporaryPassword}, closes the holder's
 * live sessions and emails the value, returning the plaintext to the ADMIN.
 *
 * <p>The {@code assertCanOperate()} gate mirrors {@code CreateUserUseCaseImpl}:
 * an ADMIN whose own password change is still pending cannot administer
 * accounts, so a compromised bootstrap credential cannot bootstrap more.
 */
public class AdminResetPasswordUseCaseImpl implements AdminResetPasswordUseCase {

	private static final Logger log = LoggerFactory.getLogger(AdminResetPasswordUseCaseImpl.class);

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;
	private final NotificationPort notificationPort;
	private final RefreshTokenRepository refreshTokenRepository;

	public AdminResetPasswordUseCaseImpl(UserRepository userRepository, PasswordHasher passwordHasher,
			TemporaryPasswordGenerator temporaryPasswordGenerator, NotificationPort notificationPort,
			RefreshTokenRepository refreshTokenRepository) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.temporaryPasswordGenerator = temporaryPasswordGenerator;
		this.notificationPort = notificationPort;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Override
	@Transactional
	public AdminResetPasswordResult reset(AdminResetPasswordCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		User target = userRepository.findById(command.targetUserId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + command.targetUserId()));

		String temporaryPassword = temporaryPasswordGenerator.generate();
		target.forceTemporaryPassword(passwordHasher.hash(temporaryPassword));
		User saved = userRepository.save(target);

		// Hand the old credential's sessions the door before the new one reaches
		// the user: whoever prompted this reset (or a thief) must not still be
		// logged in on a token minted under the previous password.
		refreshTokenRepository.revokeAllForUser(saved.getId(), Instant.now());

		// Best-effort by contract (NotificationPort): the mail adapter already
		// dispatches asynchronously, but the guarantee is restated here because a
		// reset the ADMIN has been told succeeded must not be undone by a
		// delivery failure — they still hold the plaintext in the response.
		try {
			notificationPort.sendTemporaryPassword(saved.getUsername(), temporaryPassword);
		} catch (RuntimeException ex) {
			log.warn("No se pudo enviar el correo de contraseña temporal a {}: {}", saved.getUsername(),
					ex.getMessage());
		}

		return new AdminResetPasswordResult(saved.getId(), saved.getUsername(), temporaryPassword);
	}
}