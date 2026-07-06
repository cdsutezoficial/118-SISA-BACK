package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the caller's own password and lifts the mandatory first-access
 * gate (spec: "Mandatory First-Access Password Change" — "Successful change
 * lifts the block"). This is the one operation exempt from
 * {@code User.assertCanOperate()}.
 */
public class ChangePasswordUseCaseImpl implements ChangePasswordUseCase {

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;

	public ChangePasswordUseCaseImpl(UserRepository userRepository, PasswordHasher passwordHasher) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
	}

	@Override
	@Transactional
	public void changePassword(ChangePasswordCommand command) {
		User user = userRepository.findById(command.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + command.userId()));

		if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
			throw new InvalidCredentialsException("Current password is incorrect");
		}

		String newPasswordHash = passwordHasher.hash(command.newPassword());
		user.changePassword(newPasswordHash);
		userRepository.save(user);
	}
}
