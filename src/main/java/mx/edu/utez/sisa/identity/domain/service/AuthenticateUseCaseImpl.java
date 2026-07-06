package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates local credentials and issues access + refresh tokens (spec:
 * "Authenticate with Local Credentials"). Credential matching, failed-attempt
 * counting, and auto-lock stay in the {@link User} aggregate (design.md —
 * Decision: Auth ownership); this service only orchestrates ports.
 */
public class AuthenticateUseCaseImpl implements AuthenticateUseCase {

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final PasswordHasher passwordHasher;
	private final AccessTokenIssuer accessTokenIssuer;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final RefreshTokenRepository refreshTokenRepository;
	private final Duration refreshTokenTtl;

	public AuthenticateUseCaseImpl(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PasswordHasher passwordHasher, AccessTokenIssuer accessTokenIssuer,
			RefreshTokenGenerator refreshTokenGenerator, RefreshTokenRepository refreshTokenRepository,
			Duration refreshTokenTtl) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.passwordHasher = passwordHasher;
		this.accessTokenIssuer = accessTokenIssuer;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	/**
	 * {@code noRollbackFor = InvalidCredentialsException.class}: a wrong
	 * password is an expected business outcome, not a failure this
	 * transaction should undo — the {@code registerFailedLogin()} side
	 * effect saved just before throwing (attempt count / auto-LOCK) MUST
	 * persist, or repeated wrong attempts would never actually lock the
	 * account (caught by {@code AuthFlowIT}, task 6.3/6.4 — the mocked
	 * {@code AuthenticateUseCaseImplTest} can't catch this since it never
	 * exercises a real transaction).
	 */
	@Override
	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public AuthenticationResult authenticate(AuthenticateCommand command) {
		User user = userRepository.findByUsername(command.username())
				.orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

		if (user.getStatus() == UserStatus.LOCKED) {
			throw new AccountLockedException("User account is locked: " + user.getId());
		}

		if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
			user.registerFailedLogin();
			userRepository.save(user);
			throw new InvalidCredentialsException("Invalid username or password");
		}

		user.recordSuccessfulLogin();
		userRepository.save(user);

		Set<RoleType> roles = userRoleRepository.findByUserId(user.getId()).stream().map(UserRole::getRoleType)
				.collect(Collectors.toSet());
		String accessToken = accessTokenIssuer.issue(user.getId(), roles);

		String refreshTokenValue = refreshTokenGenerator.generate();
		String refreshTokenHash = TokenHashing.sha256(refreshTokenValue);
		RefreshToken refreshToken = new RefreshToken(user.getId(), refreshTokenHash,
				Instant.now().plus(refreshTokenTtl));
		refreshTokenRepository.save(refreshToken);

		return new AuthenticationResult(accessToken, refreshTokenValue, user.isMustChangePassword());
	}
}
