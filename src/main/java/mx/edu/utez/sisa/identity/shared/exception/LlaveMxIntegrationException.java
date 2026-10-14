package mx.edu.utez.sisa.identity.shared.exception;

/**
 * Thrown when the Llave Morelos OAuth provider is unreachable, is not
 * configured, rejects a request, or answers something this integration cannot
 * interpret.
 *
 * <p>Distinct from {@code InvalidCredentialsException} on purpose: a provider
 * outage is not a failed login. Locking the account because Morelos is down
 * would lock every official trying to authenticate, so this surfaces as a 502
 * and never touches the failed-attempt counter.
 */
public class LlaveMxIntegrationException extends RuntimeException {

	public LlaveMxIntegrationException(String message) {
		super(message);
	}
}
