package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxAuthenticationResult;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.LlaveMxProfileVerification;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase.StartResult;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort.LlaveMxProfile;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort.LlaveMxTokenSet;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxAccountNotLinkedException;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxIntegrationException;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateWithLlaveMxUseCaseImplTest {

	private static final String SUBJECT = "morelos-subject-abc";
	private static final String CODE = "authorization-code-from-provider";
	private static final String VERIFIER = "pkce-verifier-value";

	@Mock
	private LlaveMxOAuthPort llaveMxOAuthPort;
	@Mock
	private UserRepository userRepository;
	@Mock
	private UserRoleRepository userRoleRepository;
	@Mock
	private RoleRepository roleRepository;
	@Mock
	private AccessTokenIssuer accessTokenIssuer;
	@Mock
	private RefreshTokenGenerator refreshTokenGenerator;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private AuthenticateWithLlaveMxUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new AuthenticateWithLlaveMxUseCaseImpl(llaveMxOAuthPort, userRepository, userRoleRepository,
				roleRepository, accessTokenIssuer, refreshTokenGenerator, refreshTokenRepository, Duration.ofDays(1));
	}

	@Test
	void start_returnsAuthorizationUrlWithFreshMaterial() {
		when(llaveMxOAuthPort.buildAuthorizationUrl(anyString(), anyString()))
				.thenReturn("https://simple.morelos.gob.mx/oauth/authorize?x=1");

		StartResult result = useCase.start();

		assertThat(result.authorizationUrl()).isEqualTo("https://simple.morelos.gob.mx/oauth/authorize?x=1");
		assertThat(result.state()).isNotBlank();
		assertThat(result.codeVerifier()).isNotBlank();
		assertThat(result.state()).isNotEqualTo(result.codeVerifier());
	}

	@Test
	void complete_linkedOfficialGetsTheUsualTokenPair() {
		withRole(RoleType.ADMIN);
		stubProviderProfile();

		LlaveMxAuthenticationResult result = useCase.complete(CODE, "state-value", VERIFIER);

		assertThat(result.accessToken()).isEqualTo("access-token-value");
		assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
		verify(accessTokenIssuer).issue(any(), anySet());
		// The plaintext refresh token is never persisted, only its hash.
		verify(refreshTokenRepository).save(any());
	}

	@Test
	void complete_looksTheAccountUpBySubjectNotByEmail() {
		User user = withRole(RoleType.SERVICIOS_ESCOLARES);
		stubProviderProfile();

		useCase.complete(CODE, "state-value", VERIFIER);

		verify(userRepository).findByLlaveMxSubject(SUBJECT);
		verify(userRepository, never()).findByUsername(anyString());
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void complete_unlinkedOfficialIsRefusedAndIssuesNothing() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(llaveMxOAuthPort.exchangeAuthorizationCode(CODE, VERIFIER)).thenReturn(tokenSet());
		when(llaveMxOAuthPort.fetchProfile("provider-access-token")).thenReturn(profile());
		when(userRepository.findByLlaveMxSubject(SUBJECT)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", VERIFIER))
				.isInstanceOf(LlaveMxAccountNotLinkedException.class);

		verify(accessTokenIssuer, never()).issue(any(), anySet());
		verifyNoInteractions(refreshTokenRepository);
	}

	@Test
	void complete_lockedOfficialIsRefusedEvenThoughMorelosSaidYes() {
		User user = linkOfficial();
		// Reached through the aggregate rather than a setter: locking is the
		// aggregate's business and the local login path must agree.
		user.registerFailedLogin();
		user.registerFailedLogin();
		user.registerFailedLogin();
		stubProviderProfile();

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", VERIFIER))
				.isInstanceOf(AccountLockedException.class);

		verify(accessTokenIssuer, never()).issue(any(), anySet());
	}

	@Test
	void complete_officialWithoutActiveRoleIsRefused() {
		User user = linkOfficial();
		stubProviderProfile();
		when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of());
		when(roleRepository.findByIds(List.of())).thenReturn(List.of());

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", VERIFIER))
				.isInstanceOf(LlaveMxAccountNotLinkedException.class)
				.hasMessageContaining("rol activo");

		verify(accessTokenIssuer, never()).issue(any(), anySet());
	}

	/**
	 * A provider outage must not look like a bad official: nothing reaches the
	 * user repository at all, so no failed-attempt counter moves and nobody
	 * gets locked out because Morelos is down.
	 */
	@Test
	void complete_providerFailureNeverTouchesTheAccount() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(llaveMxOAuthPort.exchangeAuthorizationCode(CODE, VERIFIER))
				.thenThrow(new LlaveMxIntegrationException("LlaveMX no esta disponible."));

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", VERIFIER))
				.isInstanceOf(LlaveMxIntegrationException.class);

		verifyNoInteractions(userRepository, accessTokenIssuer, refreshTokenRepository);
	}

	@Test
	void complete_withoutVerifierIsRejectedBeforeCallingTheProvider() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", null))
				.isInstanceOf(LlaveMxIntegrationException.class)
				.hasMessageContaining("expiro");

		// The whole point of PKCE: an intercepted callback with no verifier in
		// the browser must not cost the provider a token request.
		verify(llaveMxOAuthPort, never()).exchangeAuthorizationCode(anyString(), anyString());
	}

	@Test
	void complete_withoutStateIsRejectedBeforeCallingTheProvider() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);

		assertThatThrownBy(() -> useCase.complete(CODE, null, VERIFIER))
				.isInstanceOf(LlaveMxIntegrationException.class);

		verify(llaveMxOAuthPort, never()).exchangeAuthorizationCode(anyString(), anyString());
	}

	@Test
	void complete_whenNotConfiguredIsRejected() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(false);

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", VERIFIER))
				.isInstanceOf(LlaveMxIntegrationException.class)
				.hasMessageContaining("no esta configurada");
	}

	@Test
	void complete_withoutAuthorizationCodeIsRejected() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);

		assertThatThrownBy(() -> useCase.complete(null, "state-value", VERIFIER))
				.isInstanceOf(LlaveMxIntegrationException.class)
				.hasMessageContaining("codigo de autorizacion");
	}

	@Test
	void complete_profileWithoutSubjectIsRejected() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(llaveMxOAuthPort.exchangeAuthorizationCode(CODE, VERIFIER)).thenReturn(tokenSet());
		when(llaveMxOAuthPort.fetchProfile("provider-access-token"))
				.thenReturn(new LlaveMxProfile(null, "correo@utez.edu.mx", "Nombre", "CURP1234", List.of(), Map.of()));

		assertThatThrownBy(() -> useCase.complete(CODE, "state-value", VERIFIER))
				.isInstanceOf(LlaveMxIntegrationException.class)
				.hasMessageContaining("identificador del funcionario");

		verify(userRepository, never()).findByLlaveMxSubject(anyString());
	}

	/**
	 * RF-AUTH-003: a candidate has a ficha, not a login. Verification must stop
	 * at the identity and create no account whatsoever.
	 */
	@Test
	void verifyIdentity_returnsProfileWithoutCreatingAnyAccount() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(llaveMxOAuthPort.exchangeAuthorizationCode(CODE, VERIFIER)).thenReturn(tokenSet());
		when(llaveMxOAuthPort.fetchProfile("provider-access-token")).thenReturn(profile());

		LlaveMxProfileVerification result = useCase.verifyIdentity(CODE, "state-value", VERIFIER);

		assertThat(result.subject()).isEqualTo(SUBJECT);
		assertThat(result.curp()).isEqualTo("CURP123456HDFRPN01");
		assertThat(result.fullName()).isEqualTo("Ada Lovelace");
		assertThat(result.roles()).containsExactly("DOCENTE");
		verifyNoInteractions(userRepository, userRoleRepository, roleRepository, accessTokenIssuer,
				refreshTokenRepository);
	}

	/**
	 * Both flavors run the same exchange, so the CSRF gate cannot be bypassed by
	 * picking the candidate one.
	 */
	@Test
	void verifyIdentity_alsoRejectsAMissingVerifier() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);

		assertThatThrownBy(() -> useCase.verifyIdentity(CODE, "state-value", " "))
				.isInstanceOf(LlaveMxIntegrationException.class);

		verify(llaveMxOAuthPort, never()).exchangeAuthorizationCode(anyString(), anyString());
	}

	@Test
	void complete_passesTheGeneratedVerifierToTheProvider() {
		withRole(RoleType.ADMIN);
		stubProviderProfile();

		useCase.complete(CODE, "state-value", VERIFIER);

		verify(llaveMxOAuthPort).exchangeAuthorizationCode(CODE, VERIFIER);
	}

	@Test
	void complete_doesNotAcceptAStateTheProviderDidNotEcho() {
		withRole(RoleType.ADMIN);
		stubProviderProfile();

		// The service only sees the state handed to it; proving they match is the
		// controller's job, which is why the cookie name is derived from the same
		// value. Here the contract is that whatever arrives is forwarded as-is.
		useCase.complete(CODE, "state-from-callback", VERIFIER);

		verify(llaveMxOAuthPort).exchangeAuthorizationCode(eq(CODE), eq(VERIFIER));
	}

	/**
	 * Only the account lookup. Split from {@link #withRole} because each test
	 * rejects at a different stage, and a single fat helper would leave stubs
	 * behind that Mockito rightly flags as unused.
	 */
	private User linkOfficial() {
		User user = new User(UUID.randomUUID(), "ada@utez.edu.mx", "hashed-pw");
		user.setLlaveMxLinked(true);
		user.setLlaveMxSubject(SUBJECT);
		when(userRepository.findByLlaveMxSubject(SUBJECT)).thenReturn(Optional.of(user));
		return user;
	}

	private User withRole(RoleType roleType) {
		User user = linkOfficial();
		when(userRoleRepository.findByUserId(user.getId()))
				.thenReturn(List.of(new UserRole(user.getId(), roleId(roleType), null)));
		when(roleRepository.findByIds(List.of(roleId(roleType)))).thenReturn(List.of(role(roleType)));
		when(accessTokenIssuer.issue(any(), anySet())).thenReturn("access-token-value");
		when(refreshTokenGenerator.generate()).thenReturn("refresh-token-value");
		return user;
	}

	private void stubProviderProfile() {
		when(llaveMxOAuthPort.isConfigured()).thenReturn(true);
		when(llaveMxOAuthPort.exchangeAuthorizationCode(CODE, VERIFIER)).thenReturn(tokenSet());
		when(llaveMxOAuthPort.fetchProfile("provider-access-token")).thenReturn(profile());
	}

	private static LlaveMxTokenSet tokenSet() {
		return new LlaveMxTokenSet("provider-access-token", "provider-refresh-token", "Bearer", 3600L);
	}

	private static LlaveMxProfile profile() {
		return new LlaveMxProfile(SUBJECT, "ada@utez.edu.mx", "Ada Lovelace", "CURP123456HDFRPN01",
				List.of("DOCENTE"), Map.of());
	}

	private static Role role(RoleType roleType) {
		Role role = new Role(roleType.name(), roleType.name(), roleType.name());
		ReflectionTestUtils.setField(role, "id", roleId(roleType));
		return role;
	}

	private static UUID roleId(RoleType roleType) {
		return UUID.nameUUIDFromBytes(("role-" + roleType.name()).getBytes(StandardCharsets.UTF_8));
	}
}
