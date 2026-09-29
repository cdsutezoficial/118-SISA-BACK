package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidRefreshTokenException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exchanges a valid refresh token for a new access token without requiring
 * credentials again (spec: "Exchange Refresh Token for New Access Token").
 * Re-checks the owning {@code User}'s current status even though the token
 * itself may still be valid (design.md — Decision: Refresh endpoint, closing
 * the since-LOCKED security gap).
 */
public class RefreshAccessTokenUseCaseImpl implements RefreshAccessTokenUseCase {

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;
	private final AccessTokenIssuer accessTokenIssuer;

	public RefreshAccessTokenUseCaseImpl(RefreshTokenRepository refreshTokenRepository,
			UserRepository userRepository, UserRoleRepository userRoleRepository, RoleRepository roleRepository,
			AccessTokenIssuer accessTokenIssuer) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
		this.accessTokenIssuer = accessTokenIssuer;
	}

	@Override
	@Transactional
	public RefreshResult refresh(RefreshCommand command) {
		String tokenHash = TokenHashing.sha256(command.refreshToken());
		RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not recognized"));

		if (refreshToken.getRevokedAt() != null) {
			throw new InvalidRefreshTokenException("La sesión ya no está disponible.");
		}
		if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
			throw new InvalidRefreshTokenException("La sesión ha expirado.");
		}

		User owner = userRepository.findById(refreshToken.getUserId()).orElseThrow(
				() -> new UserNotFoundException("Refresh token owner not found: " + refreshToken.getUserId()));

		if (owner.getStatus() == UserStatus.LOCKED) {
			throw new AccountLockedException("Tu cuenta se encuentra bloqueada.");
		}

		List<UserRole> userRoles = userRoleRepository.findByUserId(owner.getId());
		Map<UUID, Role> rolesById = roleRepository.findByIds(userRoles.stream().map(UserRole::getRoleId).distinct().toList())
				.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
		Set<String> roles = userRoles.stream().map(UserRole::getRoleId).map(rolesById::get).filter(role -> role != null)
				.map(Role::getKey).collect(Collectors.toSet());
		String accessToken = accessTokenIssuer.issue(owner.getId(), roles);

		return new RefreshResult(accessToken);
	}
}
