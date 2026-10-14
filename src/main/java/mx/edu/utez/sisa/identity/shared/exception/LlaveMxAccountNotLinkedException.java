package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when a Llave Morelos flow completes but the authenticated official has
 * no SISA account that may authenticate with it.
 *
 * <p>Not an {@code InvalidCredentialsException}: the official proved who they
 * are, SISA simply has nothing to log them into. Keeping it separate lets the
 * message say so, and keeps the local failed-attempt counter untouched.
 */
public class LlaveMxAccountNotLinkedException extends RuntimeException {

	public LlaveMxAccountNotLinkedException(String message) {
		super(message);
	}
}
