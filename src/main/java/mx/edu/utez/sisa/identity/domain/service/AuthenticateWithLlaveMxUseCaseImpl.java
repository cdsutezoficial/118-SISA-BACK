package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateWithLlaveMxUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxPkceGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxAccountNotLinkedException;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxIntegrationException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implements both Llave Morelos entry points over the same OAuth exchange.
 *
 * <p>Two consumers, one flow (RF-AUTH-002 and RF-AUTH-003):
 * <ul>
 * <li>{@link #complete} — staff login. Resolves the official to a
 * {@link User} by OAuth subject and issues the same token pair the local login
 * does.</li>
 * <li>{@link #verifyIdentity} — candidate verification. Stops at the identity
 * and deliberately creates no account: the candidate has a ficha folio, not a
 * login (er-02-identidad.md, "Candidatos no tienen {@code User}").</li>
 * </ul>
 *
 * <p>A local account is <em>linked</em> to a subject on first successful LlaveMX
 * login, keyed by the subject — never by the email, which the provider does not
 * guarantee is unique across Morelos deployments.
 */
public class AuthenticateWithLlaveMxUseCaseImpl implements AuthenticateWithLlaveMxUseCase {

	private static final Logger log = LoggerFactory.getLogger(AuthenticateWithLlaveMxUseCaseImpl.class);

	private final LlaveMxOAuthPort llaveMxOAuthPort;
	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;
	private final AccessTokenIssuer accessTokenIssuer;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final RefreshTokenRepository refreshTokenRepository;
	private final Duration refreshTokenTtl;

	public AuthenticateWithLlaveMxUseCaseImpl(LlaveMxOAuthPort llaveMxOAuthPort, UserRepository userRepository,
			UserRoleRepository userRoleRepository, RoleRepository roleRepository,
			AccessTokenIssuer accessTokenIssuer, RefreshTokenGenerator refreshTokenGenerator,
			RefreshTokenRepository refreshTokenRepository, Duration refreshTokenTtl) {
		this.llaveMxOAuthPort = llaveMxOAuthPort;
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	@Override
	public StartResult start() {
		String state = LlaveMxPkceGenerator.newState();
		String verifier = LlaveMxPkceGenerator.newCodeVerifier();
		String challenge = LlaveMxPkceGenerator.challengeOf(verifier);
		return new StartResult(llaveMxOAuthPort.buildAuthorizationUrl(state, challenge), state, verifier);
	}

	@Override
	@Transactional
	public LlaveMxAuthenticationResult complete(String authorizationCode, String expectedState,
			String codeVerifier) {
		LlaveMxOAuthPort.LlaveMxProfile profile = exchange(authorizationCode, expectedState, codeVerifier);

		// The subject is exactly the value an administrator has to write into
		// llave_mx_subject, and no screen shows it anywhere. It is logged rather
		// than put in the message on purpose: the message travels back to the
		// browser inside a URL, and the subject is a CURP — personal data the
		// address bar has no business holding. The administrator who has to run
		// the UPDATE is the one reading the log.
		java.util.Optional<User> linked = userRepository.findByLlaveMxSubject(profile.subject());
		if (linked.isEmpty()) {
			log.info("LlaveMX sin vincular: subject='{}' (asocialo en llave_mx_subject para habilitar la cuenta)",
					profile.subject());
			throw new LlaveMxAccountNotLinkedException(
					"Tu cuenta de SISA no esta vinculada a LlaveMX. Pide a un administrador que la vincule.");
		}
		User user = linked.get();

		// Same gate as the local login: a locked account stays locked no matter
		// which credential proved the identity.
		if (user.getStatus() == UserStatus.LOCKED) {
			throw new AccountLockedException("Tu cuenta se encuentra bloqueada.");
		}

		Set<String> roles = activeRoleKeys(user);
		if (roles.isEmpty()) {
			throw new LlaveMxAccountNotLinkedException(
					"Tu cuenta no tiene ningun rol activo, por lo que no puede iniciar sesion.");
		}

		user.recordSuccessfulLogin();
		userRepository.save(user);

		String accessToken = accessTokenIssuer.issue(user.getId(), roles);
		String refreshTokenValue = refreshTokenGenerator.generate();
		refreshTokenRepository.save(new RefreshToken(user.getId(), TokenHashing.sha256(refreshTokenValue),
				Instant.now().plus(refreshTokenTtl)));
		return new LlaveMxAuthenticationResult(user.getId(), accessToken, refreshTokenValue,
				user.isMustChangePassword());
	}

	@Override
	public LlaveMxProfileVerification verifyIdentity(String authorizationCode, String expectedState,
			String codeVerifier) {
		LlaveMxOAuthPort.LlaveMxProfile profile = exchange(authorizationCode, expectedState, codeVerifier);
		return new LlaveMxProfileVerification(profile.subject(), profile.institutionalEmail(), profile.fullName(),
				profile.curp(), profile.roles(), profile.prefill());
	}

	@Override
	@Transactional
	public LlaveMxLinkResult link(String authorizationCode, String expectedState, String codeVerifier, UUID userId) {
		LlaveMxOAuthPort.LlaveMxProfile profile = exchange(authorizationCode, expectedState, codeVerifier);

		User target = userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

		// Same gate as a LlaveMX login: a locked account must not gain a second
		// way in while the first one is shut.
		if (target.getStatus() == UserStatus.LOCKED) {
			throw new AccountLockedException("La cuenta se encuentra bloqueada.");
		}

		// llave_mx_subject is the key `complete` resolves a login by, so exactly
		// one account may hold it. Overwriting another account's binding would
		// hand this person a login to someone else's rows; the administrator is
		// told which side to fix instead.
		java.util.Optional<User> holder = userRepository.findByLlaveMxSubject(profile.subject());
		if (holder.isPresent() && !holder.get().getId().equals(userId)) {
			log.warn("LlaveMX rechazo vincular subject a {}: ya pertenece a otro usuario", userId);
			throw new LlaveMxAccountNotLinkedException(
					"Ya existe una cuenta de SISA vinculada a esa identidad de LlaveMX.");
		}

		target.setLlaveMxSubject(profile.subject());
		target.setLlaveMxLinked(true);
		userRepository.save(target);
		// Subject is a CURP: personal data, so it goes to the log and never into
		// a message the browser will render in an address bar.
		log.info("LlaveMX vinculado: usuario {} con subject='{}'", userId, profile.subject());
		return new LlaveMxLinkResult(userId);
	}

	/**
	 * Shared exchange + CSRF check. A state mismatch throws before any token
	 * request, so a forged callback costs the provider nothing.
	 */
	private LlaveMxOAuthPort.LlaveMxProfile exchange(String authorizationCode, String expectedState,
			String codeVerifier) {
		if (!llaveMxOAuthPort.isConfigured()) {
			throw new LlaveMxIntegrationException(
					"La integracion con LlaveMX no esta configurada en este ambiente.");
		}
		if (expectedState == null || expectedState.isBlank() || codeVerifier == null || codeVerifier.isBlank()) {
			// No state/verifier came back from the cookie: the flow was never
			// started in this browser, or the cookie expired.
			throw new LlaveMxIntegrationException(
					"La sesion de verificacion con LlaveMX expiro. Intenta de nuevo desde el inicio.");
		}
		if (authorizationCode == null || authorizationCode.isBlank()) {
			throw new LlaveMxIntegrationException("LlaveMX no devolvio un codigo de autorizacion.");
		}
		LlaveMxOAuthPort.LlaveMxTokenSet tokens = llaveMxOAuthPort.exchangeAuthorizationCode(authorizationCode,
				codeVerifier);
		LlaveMxOAuthPort.LlaveMxProfile profile = llaveMxOAuthPort.fetchProfile(tokens.accessToken());
		if (profile.subject() == null || profile.subject().isBlank()) {
			throw new LlaveMxIntegrationException("LlaveMX no devolvio el identificador del funcionario.");
		}
		return profile;
	}

	private Set<String> activeRoleKeys(User user) {
		List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
		Map<UUID, Role> rolesById = roleRepository
				.findByIds(userRoles.stream().map(UserRole::getRoleId).distinct().toList()).stream()
				.collect(Collectors.toMap(Role::getId, Function.identity()));
		return userRoles.stream().map(UserRole::getRoleId).map(rolesById::get).filter(java.util.Objects::nonNull)
				.map(Role::getKey).collect(Collectors.toSet());
	}
}
