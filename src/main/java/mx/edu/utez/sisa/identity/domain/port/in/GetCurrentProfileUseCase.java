package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.UUID;

/**
 * Self-service profile lookup backing {@code GET /auth/me}: the caller's own
 * fullName (from its linked {@code Person}), username and best email — never
 * anyone else's. The acting userId comes from the JWT {@code sub} claim, so a
 * caller can only ever learn about itself. Mirrors
 * {@code GetUserUseCaseImpl#fullName} for name assembly while keeping the
 * result deliberately lean (no roles/status — those are already in the JWT
 * and the capability envelope).
 */
public interface GetCurrentProfileUseCase {

	CurrentProfileResult getCurrentProfile(CurrentProfileQuery query);

	record CurrentProfileQuery(UUID userId) {
	}

	record CurrentProfileResult(UUID userId, String fullName, String username, String email) {
	}
}