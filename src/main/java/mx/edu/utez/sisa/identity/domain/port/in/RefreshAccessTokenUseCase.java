package mx.edu.utez.sisa.identity.domain.port.in;

/**
 * Exchanges a valid, non-expired, non-revoked refresh token for a new access
 * token, without requiring credentials again (spec: "Exchange Refresh Token
 * for New Access Token"). Public — no caller gate; implementations must also
 * reject the token if the owning {@code User}'s current status is
 * {@code LOCKED}, even if the token itself is still valid (design.md —
 * refresh endpoint decision), while {@code mustChangePassword = true} does
 * NOT block refresh (mirrors the login exception, so the user can still
 * reach {@code ChangePasswordUseCase} after refreshing).
 */
public interface RefreshAccessTokenUseCase {

	RefreshResult refresh(RefreshCommand command);

	record RefreshCommand(String refreshToken) {
	}

	record RefreshResult(String accessToken) {
	}
}
