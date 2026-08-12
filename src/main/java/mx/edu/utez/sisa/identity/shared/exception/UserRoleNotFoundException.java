package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when {@code RevokeRoleUseCase} is invoked with a {@code userRoleId}
 * that either does not exist or does not belong to the {@code userId} in the
 * URL (plan: {@code docs/plans/2026-07-28-persons-and-user-management.md} —
 * 4.4 "evita revocar el rol de otro usuario adivinando un id"). Both cases
 * map to the same 404 — the caller cannot distinguish "wrong id" from
 * "someone else's role" from the outside.
 */
public class UserRoleNotFoundException extends RuntimeException {

	public UserRoleNotFoundException(String message) {
		super(message);
	}
}
