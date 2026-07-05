package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for generating the plaintext opaque refresh token value handed
 * to the client. Only its SHA-256 hash is persisted (design.md — Decision:
 * Refresh endpoint); the generator itself has no knowledge of hashing.
 */
public interface RefreshTokenGenerator {

	String generate();
}
