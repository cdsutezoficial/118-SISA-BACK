package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.PasswordResetToken;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.RequestPasswordResetUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.NotificationPort;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Starts the "forgot my password" flow (01-identidad.md —
 * RequestPasswordResetUseCase): looks the user up by institutional email,
 * generates an opaque single-use token, persists only its SHA-256 hash, marks
 * any previous tokens of the same user as used (the one-active-token invariant),
 * and emails the reset link. A request for an unknown {@code username} succeeds
 * silently — no token, no email — so the endpoint never reveals whether an
 * account exists.
 */
public class RequestPasswordResetUseCaseImpl implements RequestPasswordResetUseCase {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordResetTokenGenerator passwordResetTokenGenerator;
	private final NotificationPort notificationPort;
	private final Duration tokenTtl;
	private final String frontendBaseUrl;

	public RequestPasswordResetUseCaseImpl(UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordResetTokenGenerator passwordResetTokenGenerator, NotificationPort notificationPort,
			Duration tokenTtl, String frontendBaseUrl) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordResetTokenGenerator = passwordResetTokenGenerator;
		this.notificationPort = notificationPort;
		this.tokenTtl = tokenTtl;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	@Override
	@Transactional
	public void request(RequestPasswordResetCommand command) {
		User user = userRepository.findByUsername(command.username()).orElse(null);
		if (user == null) {
			return;
		}

		String plainToken = passwordResetTokenGenerator.generate();
		String tokenHash = TokenHashing.sha256(plainToken);
		Instant expiresAt = Instant.now().plus(tokenTtl);

		List<PasswordResetToken> previous = passwordResetTokenRepository.findByUserId(user.getId());
		if (previous != null) {
			for (PasswordResetToken token : previous) {
				if (!token.isUsed()) {
					token.markUsed();
					passwordResetTokenRepository.save(token);
				}
			}
		}

		passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), tokenHash, expiresAt));

		String resetLink = frontendBaseUrl + "/reset-confirm?token=" + plainToken;
		notificationPort.sendPasswordReset(user.getUsername(), resetLink);
	}
}