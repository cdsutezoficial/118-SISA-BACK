package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.PasswordResetToken;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ResetPasswordUseCase.ResetPasswordCommand;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.InvalidPasswordResetTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseImplTest {

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordHasher passwordHasher;

	private ResetPasswordUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ResetPasswordUseCaseImpl(passwordResetTokenRepository, userRepository, passwordHasher);
	}

	@Test
	void reset_unknownTokenIsRejected() {
		when(passwordResetTokenRepository.findByTokenHash(TokenHashing.sha256("ghost-token")))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.reset(new ResetPasswordCommand("ghost-token", "NewPass!1")))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void reset_usedTokenIsRejected() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-old");
		PasswordResetToken token = new PasswordResetToken(user.getId(), TokenHashing.sha256("token"),
				Instant.now().plusSeconds(1800));
		token.markUsed();
		when(passwordResetTokenRepository.findByTokenHash(TokenHashing.sha256("token")))
				.thenReturn(Optional.of(token));

		assertThatThrownBy(() -> useCase.reset(new ResetPasswordCommand("token", "NewPass!1")))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void reset_expiredTokenIsRejected() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-old");
		PasswordResetToken token = new PasswordResetToken(user.getId(), TokenHashing.sha256("token"),
				Instant.now().minusSeconds(1));
		when(passwordResetTokenRepository.findByTokenHash(TokenHashing.sha256("token")))
				.thenReturn(Optional.of(token));

		assertThatThrownBy(() -> useCase.reset(new ResetPasswordCommand("token", "NewPass!1")))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void reset_successChangesPasswordAndMarksTokenUsed() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-old");
		PasswordResetToken token = new PasswordResetToken(user.getId(), TokenHashing.sha256("valid-token"),
				Instant.now().plusSeconds(1800));
		when(passwordResetTokenRepository.findByTokenHash(TokenHashing.sha256("valid-token")))
				.thenReturn(Optional.of(token));
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(passwordHasher.hash("NewPass!1")).thenReturn("hashed-new");

		assertThatCode(() -> useCase.reset(new ResetPasswordCommand("valid-token", "NewPass!1")))
				.doesNotThrowAnyException();

		assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
		assertThat(user.isMustChangePassword()).isFalse();
		assertThat(token.isUsed()).isTrue();
		verify(userRepository).save(user);
		verify(passwordResetTokenRepository).save(token);
	}
}