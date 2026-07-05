package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.shared.model.RoleType;

import java.util.Set;
import java.util.UUID;

/**
 * Out-port for issuing signed access tokens. Implemented by a {@code jjwt}
 * adapter in Phase 4 (design.md — access token: 30-minute TTL, claims
 * {@code sub} = userId and {@code roles} = the user's {@code RoleType}
 * names only).
 */
public interface AccessTokenIssuer {

	String issue(UUID userId, Set<RoleType> roles);
}
