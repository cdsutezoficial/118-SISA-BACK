package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when authentication or refresh is attempted against a {@code User}
 * whose status is {@code LOCKED} (spec: "Locked account rejects even
 * correct credentials" / "Refresh token of a since-LOCKED user is
 * rejected").
 */
public class AccountLockedException extends RuntimeException {

	public AccountLockedException(String message) {
		super(message);
	}
}
