package mx.edu.utez.sisa.identity.shared.exception;

/**
 * The applicant reached the registration form without a valid Llave Morelos
 * verification ticket, while this environment requires one (RF-AUTH-003).
 *
 * <p>Separate from {@link LlaveMxIntegrationException} because the two mean
 * opposite things to the applicant and to the operator: this one is "you have
 * not proven your identity yet, go and do it" (the caller's own fault, fixable
 * in the flow), while that one is "the provider is unreachable or misconfigured"
 * (nobody's fault, retry later). They must not share an HTTP status.
 */
public class LlaveMxVerificationRequiredException extends RuntimeException {

	public LlaveMxVerificationRequiredException(String message) {
		super(message);
	}
}
