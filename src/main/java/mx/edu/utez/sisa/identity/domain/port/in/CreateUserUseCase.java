package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.UUID;

/**
 * Creates a staff {@code User} account for an existing {@code Person} (spec:
 * "Create User Account"). Restricted to ADMIN callers; implementations must
 * enforce the caller's {@code assertCanOperate()} gate before creating the
 * account (design.md — Decision: mustChangePassword gate).
 */
public interface CreateUserUseCase {

	UserCreationResult createUser(CreateUserCommand command);

	/**
	 * @param callerId       the acting ADMIN user, used for the mustChangePassword guard and authorization
	 * @param personId       the pre-existing {@code Person} to attach the new account to
	 * @param temporaryPassword the initial plaintext password, hashed by the implementation
	 */
	record CreateUserCommand(UUID callerId, UUID personId, String temporaryPassword) {
	}

	record UserCreationResult(UUID userId, String username, boolean mustChangePassword) {
	}
}
