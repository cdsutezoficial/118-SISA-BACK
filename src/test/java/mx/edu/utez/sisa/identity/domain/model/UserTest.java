package mx.edu.utez.sisa.identity.domain.model;

import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

	@Test
	void registerFailedLogin_incrementsAttemptsWithoutLocking() {
		User user = newActiveUser();

		user.registerFailedLogin();

		assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void registerFailedLogin_thirdConsecutiveFailureLocksAccount() {
		User user = newActiveUser();

		user.registerFailedLogin();
		user.registerFailedLogin();
		user.registerFailedLogin();

		assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
		assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
	}

	@Test
	void assertCanOperate_throwsWhenMustChangePasswordIsPending() {
		User user = newActiveUser();

		assertThatThrownBy(user::assertCanOperate).isInstanceOf(MustChangePasswordException.class);
	}

	@Test
	void changePassword_clearsMustChangePasswordAndUpdatesHash() {
		User user = newActiveUser();

		user.changePassword("new-hashed-pw");

		assertThat(user.isMustChangePassword()).isFalse();
		assertThat(user.getPasswordHash()).isEqualTo("new-hashed-pw");
	}

	@Test
	void changePassword_liftsTheAssertCanOperateBlock() {
		User user = newActiveUser();

		user.changePassword("new-hashed-pw");

		assertThatCode(user::assertCanOperate).doesNotThrowAnyException();
	}

	@Test
	void recordSuccessfulLogin_resetsFailedAttemptsAndSetsLastLoginAt() {
		User user = newActiveUser();
		user.registerFailedLogin();
		user.registerFailedLogin();

		user.recordSuccessfulLogin();

		assertThat(user.getFailedLoginAttempts()).isZero();
		assertThat(user.getLastLoginAt()).isNotNull();
	}

	private User newActiveUser() {
		return new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
	}
}
