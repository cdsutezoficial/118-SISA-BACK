package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.RefreshToken;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase.RefreshCommand;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase.RefreshResult;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshAccessTokenUseCaseImplTest {

	private static final String RAW_TOKEN = "opaque-refresh-token-value";

	@Mock
	private RefreshTokenRepository refreshTokenRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private UserRoleRepository userRoleRepository;
	@Mock
	private AccessTokenIssuer accessTokenIssuer;

	private RefreshAccessTokenUseCaseImpl useCase;

	private User owner;
	private UUID ownerId;

	@BeforeEach
	void setUp() {
		useCase = new RefreshAccessTokenUseCaseImpl(refreshTokenRepository, userRepository, userRoleRepository,
				accessTokenIssuer);
		owner = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
		ownerId = UUID.randomUUID();
		ReflectionTestUtils.setField(owner, "id", ownerId);
	}

	private RefreshToken tokenFor(UUID userId, Instant expiresAt) {
		return new RefreshToken(userId, TokenHashing.sha256(RAW_TOKEN), expiresAt);
	}

	@Test
	void refresh_validNonExpiredNonRevokedTokenIssuesANewAccessToken() {
		RefreshToken token = tokenFor(ownerId, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(TokenHashing.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));
		when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(userRoleRepository.findByUserId(ownerId)).thenReturn(List.of());
		when(accessTokenIssuer.issue(any(), anySet())).thenReturn("new-access-token");

		RefreshResult result = useCase.refresh(new RefreshCommand(RAW_TOKEN));

		assertThat(result.accessToken()).isEqualTo("new-access-token");
	}

	@Test
	void refresh_expiredTokenIsRejected() {
		RefreshToken token = tokenFor(ownerId, Instant.now().minusSeconds(10));
		when(refreshTokenRepository.findByTokenHash(TokenHashing.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> useCase.refresh(new RefreshCommand(RAW_TOKEN)))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void refresh_revokedTokenIsRejected() {
		RefreshToken token = tokenFor(ownerId, Instant.now().plusSeconds(3600));
		token.setRevokedAt(Instant.now().minusSeconds(5));
		when(refreshTokenRepository.findByTokenHash(TokenHashing.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));

		assertThatThrownBy(() -> useCase.refresh(new RefreshCommand(RAW_TOKEN)))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void refresh_unknownTokenIsRejected() {
		when(refreshTokenRepository.findByTokenHash(TokenHashing.sha256(RAW_TOKEN))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.refresh(new RefreshCommand(RAW_TOKEN)))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void refresh_ownerSinceLockedIsRejectedEvenIfTokenIsStillValid() {
		owner.registerFailedLogin();
		owner.registerFailedLogin();
		owner.registerFailedLogin();
		RefreshToken token = tokenFor(ownerId, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(TokenHashing.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));
		when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> useCase.refresh(new RefreshCommand(RAW_TOKEN)))
				.isInstanceOf(AccountLockedException.class);
	}

	@Test
	void refresh_ownerWithMustChangePasswordStillSucceeds() {
		// owner is freshly constructed -> mustChangePassword=true by default
		RefreshToken token = tokenFor(ownerId, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByTokenHash(TokenHashing.sha256(RAW_TOKEN))).thenReturn(Optional.of(token));
		when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(userRoleRepository.findByUserId(ownerId)).thenReturn(List.of());
		when(accessTokenIssuer.issue(any(), anySet())).thenReturn("new-access-token");

		RefreshResult result = useCase.refresh(new RefreshCommand(RAW_TOKEN));

		assertThat(result.accessToken()).isEqualTo("new-access-token");
	}
}
