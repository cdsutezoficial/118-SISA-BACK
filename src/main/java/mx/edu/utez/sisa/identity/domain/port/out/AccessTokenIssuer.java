package mx.edu.utez.sisa.identity.domain.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * Out-port for issuing signed access tokens. Implemented by a {@code jjwt}
 * adapter in Phase 4 (design.md — access token: 30-minute TTL, claims
 * {@code sub} = userId and {@code roles} = the user's persisted role keys).
 */
public interface AccessTokenIssuer {

	String issue(UUID userId, Set<String> roles);
}
