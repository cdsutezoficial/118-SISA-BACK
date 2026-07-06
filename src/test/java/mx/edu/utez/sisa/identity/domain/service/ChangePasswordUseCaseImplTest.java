package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordHasher passwordHasher;

	private ChangePasswordUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ChangePasswordUseCaseImpl(userRepository, passwordHasher);
	}

	@Test
	void changePassword_wrongCurrentPasswordIsRejected() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-current-pw");
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(passwordHasher.matches("wrong-current", "hashed-current-pw")).thenReturn(false);

		assertThatThrownBy(() -> useCase
				.changePassword(new ChangePasswordCommand(user.getId(), "wrong-current", "new-password")))
				.isInstanceOf(InvalidCredentialsException.class);

		assertThat(user.isMustChangePassword()).isTrue();
	}

	@Test
	void changePassword_successClearsMustChangePasswordFlag() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-current-pw");
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(passwordHasher.matches("current-password", "hashed-current-pw")).thenReturn(true);
		when(passwordHasher.hash("new-password")).thenReturn("hashed-new-pw");

		assertThatCode(() -> useCase
				.changePassword(new ChangePasswordCommand(user.getId(), "current-password", "new-password")))
				.doesNotThrowAnyException();

		assertThat(user.isMustChangePassword()).isFalse();
		assertThat(user.getPasswordHash()).isEqualTo("hashed-new-pw");
	}

	@Test
	void changePassword_unknownUserIsRejected() {
		UUID unknownId = UUID.randomUUID();
		when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.changePassword(new ChangePasswordCommand(unknownId, "current", "new-password")))
				.isInstanceOf(UserNotFoundException.class);
	}
}
