package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for LlaveMX OAuth login. Out of scope for this slice — only a
 * stub adapter exists (design.md — Purpose: "LlaveMX OAuth login... out of
 * scope for this slice (stub port only)"). Declared now so the domain layer
 * does not need to change shape when the real integration lands.
 */
public interface LlaveMxOAuthPort {

	LlaveMxProfile exchangeAuthorizationCode(String authorizationCode);

	record LlaveMxProfile(String subject, String institutionalEmail) {
	}
}
