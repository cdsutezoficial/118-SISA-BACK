package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for generating the plaintext temporary password an ADMIN assigns to
 * a user who cannot access their account (plan
 * {@code 2026-10-02-admin-reset-password.md} —
 * {@code AdminResetPasswordUseCase}).
 *
 * <p>Distinct from {@link PasswordResetTokenGenerator}: that one produces an
 * opaque string destined for a URL and is persisted only as a hash, whereas this
 * one produces a value the human must be able to read, retype and copy off a
 * screen — so the policy (length and character classes) is part of the contract,
 * not an implementation detail.
 */
public interface TemporaryPasswordGenerator {

	String generate();
}