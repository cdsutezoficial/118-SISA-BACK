package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.RevokeRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserRoleNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a single scoped role grant from a {@code User} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.4), the
 * inverse of {@code AssignRoleUseCaseImpl}. Deliberately minimalist: no
 * "must keep at least one role"/"must keep at least one ADMIN" guard — not
 * documented anywhere, so none is invented here (plan's explicit scope
 * note).
 */
public class RevokeRoleUseCaseImpl implements RevokeRoleUseCase {

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;

	public RevokeRoleUseCaseImpl(UserRepository userRepository, UserRoleRepository userRoleRepository) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
	}

	@Override
	@Transactional
	public void revokeRole(RevokeRoleCommand command) {
		User caller = userRepository.findById(command.callerId())
				.orElseThrow(() -> new UserNotFoundException("Caller not found: " + command.callerId()));
		caller.assertCanOperate();

		UserRole userRole = userRoleRepository.findById(command.userRoleId())
				.orElseThrow(() -> new UserRoleNotFoundException("Role grant not found: " + command.userRoleId()));

		// A UserRole that exists but belongs to a different user maps to the
		// same 404 as one that doesn't exist at all (plan 4.4: "evita revocar
		// el rol de otro usuario adivinando un id") — the caller cannot tell
		// the two cases apart from the outside.
		if (!userRole.getUserId().equals(command.userId())) {
			throw new UserRoleNotFoundException(
					"Role grant " + command.userRoleId() + " does not belong to user " + command.userId());
		}

		userRoleRepository.delete(userRole);
	}
}
