package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for generating the plaintext opaque password-reset token handed to
 * the user by email. Only its SHA-256 hash is persisted (01-identidad.md —
 * PasswordResetToken "El token plano nunca se almacena"); the generator itself
 * has no knowledge of hashing.
 */
public interface PasswordResetTokenGenerator {

	String generate();
}