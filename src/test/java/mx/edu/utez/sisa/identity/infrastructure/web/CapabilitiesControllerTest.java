package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CapabilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@code GET /auth/me/capabilities}: the caller's {@code ROLE_*}
 * authorities are extracted into role keys, resolved to the (sorted) union of
 * their permission keys, and returned as a base64url-encoded JSON array (not
 * plaintext, not in the access token) — with non-role authorities and absent
 * authentication handled safely.
 */
@ExtendWith(MockitoExtension.class)
class CapabilitiesControllerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private PermissionCache permissionCache;

	private CapabilitiesController controller;

	@BeforeEach
	void setUp() {
		controller = new CapabilitiesController(permissionCache, objectMapper);
	}

	@Test
	void returnsBase64EncodedUnionOfPermissionKeysAcrossEveryCallerRole() throws Exception {
		when(permissionCache.permissionKeysFor(anyCollection()))
				.thenReturn(Set.of("DIVISIONS_READ", "USERS_READ"));

		ResponseEntity<CapabilityResponse> response = controller
				.capabilities(new UsernamePasswordAuthenticationToken("uuid-1", null,
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
								new SimpleGrantedAuthority("ROLE_SERVICIOS_ESCOLARES"))));

		List<String> decoded = decode(response);
		assertThat(decoded).containsExactly("DIVISIONS_READ", "USERS_READ");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<String>> roleKeys = ArgumentCaptor.forClass(Collection.class);
		verify(permissionCache).permissionKeysFor(roleKeys.capture());
		assertThat(roleKeys.getValue()).containsExactlyInAnyOrder("ADMIN", "SERVICIOS_ESCOLARES");
	}

	@Test
	void ignoresNonRoleAuthorities() throws Exception {
		when(permissionCache.permissionKeysFor(anyCollection())).thenReturn(Set.of("ROLES_READ"));

		ResponseEntity<CapabilityResponse> response = controller
				.capabilities(new UsernamePasswordAuthenticationToken("uuid-2", null,
						List.of(new SimpleGrantedAuthority("SCOPE_read"),
								new SimpleGrantedAuthority("ROLE_DIRECTOR_DIVISION"))));

		List<String> decoded = decode(response);
		assertThat(decoded).containsExactly("ROLES_READ");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<String>> roleKeys = ArgumentCaptor.forClass(Collection.class);
		verify(permissionCache).permissionKeysFor(roleKeys.capture());
		assertThat(roleKeys.getValue()).containsExactly("DIRECTOR_DIVISION");
	}

	@Test
	void returnsEmptyEncodedListForAnonymousRequestWithNoAuthentication() throws Exception {
		when(permissionCache.permissionKeysFor(anyCollection())).thenReturn(Set.of());

		ResponseEntity<CapabilityResponse> response = controller.capabilities(null);

		assertThat(decode(response)).isEmpty();
	}

	@Test
	void envelopeIsNotThePlaintextPermissionList() throws Exception {
		when(permissionCache.permissionKeysFor(anyCollection()))
				.thenReturn(Set.of("USERS_READ", "DIVISIONS_READ"));

		ResponseEntity<CapabilityResponse> response = controller
				.capabilities(new UsernamePasswordAuthenticationToken("uuid-4", null,
						List.of(new SimpleGrantedAuthority("ROLE_SERVICIOS_ESCOLARES"))));

		String raw = response.getBody().capabilities();
		assertThat(raw).doesNotContain("USERS_READ", "DIVISIONS_READ", "[", "]");
		List<String> decoded = decode(response);
		assertThat(decoded).containsExactly("DIVISIONS_READ", "USERS_READ");
	}

	private List<String> decode(ResponseEntity<CapabilityResponse> response) throws Exception {
		String raw = response.getBody().capabilities();
		byte[] bytes = Base64.getUrlDecoder().decode(raw);
		return objectMapper.readValue(bytes, new TypeReference<>() {
		});
	}
}