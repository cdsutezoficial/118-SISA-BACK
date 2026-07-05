package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port over password hashing. Implemented by a BCrypt (strength 12)
 * adapter in Phase 4 (design.md — Decision: Password hashing).
 */
public interface PasswordHasher {

	String hash(String rawPassword);

	boolean matches(String rawPassword, String hashedPassword);
}
