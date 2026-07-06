package mx.edu.utez.sisa.identity.infrastructure.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Extracts the acting {@code User}'s id from the current
 * {@code SecurityContext} (design.md — Decision: mustChangePassword gate:
 * "userId extracted from the JWT principal by the controller, passed as a
 * plain command field"). {@link mx.edu.utez.sisa.identity.infrastructure.security.JwtAuthenticationFilter}
 * sets the principal to the userId string parsed from the token's
 * {@code sub} claim.
 */
final class AuthenticatedCaller {

	private AuthenticatedCaller() {
	}

	static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}
}
