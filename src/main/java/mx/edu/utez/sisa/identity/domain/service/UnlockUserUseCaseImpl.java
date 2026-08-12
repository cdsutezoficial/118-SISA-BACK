package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manually reverses a {@code User}'s account lock (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.5),
 * delegating the actual state transition to the new idempotent
 * {@code User#unlock} domain method.
 */
public class UnlockUserUseCaseImpl implements UnlockUserUseCase {

	private final UserRepository userRepository;

	public UnlockUserUseCaseImpl(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional
	public UnlockUserResult unlockUser(UnlockUserCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		User target = userRepository.findById(command.userId())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + command.userId()));

		target.unlock();
		User saved = userRepository.save(target);

		return new UnlockUserResult(saved.getId(), saved.getStatus(), saved.getFailedLoginAttempts());
	}
}
