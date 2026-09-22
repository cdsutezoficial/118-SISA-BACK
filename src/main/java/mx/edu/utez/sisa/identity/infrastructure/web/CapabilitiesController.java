package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CapabilityResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Self-service endpoint {@code GET /auth/me/capabilities}: returns the union of
 * permission keys held by the CALLER's own JWT roles, resolved from the
 * in-memory {@link PermissionCache} — no database query. The keys are NOT sent
 * as plaintext: they arrive as a single base64url-encoded JSON array, and the
 * access token itself stays lean (no permission claim — roles-permisos.md
 * §3.2). Any authenticated user may call it, and only ever learns the
 * permissions of roles it actually holds (taken from its own token's
 * {@code ROLE_*} authorities).
 * <p>
 * The fine-grained {@code PermissionFilter} remains the real authority — this
 * envelope only drives the UI, so it is encoded (obfuscation for wire
 * inspection), not signed: a client cannot verify an HMAC without the secret,
 * and tampering it only degrades the tamperer's own view.
 */
@RestController
@RequestMapping("/auth/me")
public class CapabilitiesController {

	private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	private final PermissionCache permissionCache;

	private final ObjectMapper objectMapper;

	public CapabilitiesController(PermissionCache permissionCache, ObjectMapper objectMapper) {
		this.permissionCache = permissionCache;
		this.objectMapper = objectMapper;
	}

	@GetMapping("/capabilities")
	public ResponseEntity<CapabilityResponse> capabilities(Authentication authentication) throws JsonProcessingException {
		List<String> permissionKeys = permissionCache.permissionKeysFor(roleKeys(authentication)).stream().sorted()
				.toList();
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(objectMapper.writeValueAsBytes(permissionKeys));
		return ResponseEntity.ok(new CapabilityResponse(encoded));
	}

	private static Set<String> roleKeys(Authentication authentication) {
		Set<String> roleKeys = new HashSet<>();
		if (authentication != null) {
			for (GrantedAuthority authority : authentication.getAuthorities()) {
				String value = authority.getAuthority();
				if (value != null && value.startsWith(ROLE_AUTHORITY_PREFIX)) {
					roleKeys.add(value.substring(ROLE_AUTHORITY_PREFIX.length()));
				}
			}
		}
		return roleKeys;
	}
}