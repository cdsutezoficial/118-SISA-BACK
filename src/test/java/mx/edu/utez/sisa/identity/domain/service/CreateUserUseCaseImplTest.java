package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.CreateUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.UserCreationResult;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.MissingInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PersonRepository personRepository;
	@Mock
	private PasswordHasher passwordHasher;

	private CreateUserUseCaseImpl useCase;

	private User adminCaller;

	@BeforeEach
	void setUp() {
		useCase = new CreateUserUseCaseImpl(userRepository, personRepository, passwordHasher);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2"); // clears mustChangePassword so the caller can operate
	}

	@Test
	void createUser_successfulStaffAccountCreation() {
		UUID personId = UUID.randomUUID();
		Person person = new Person("CURP000000HDFRRN01", "Jane", "Doe", null, "jane.doe@utez.edu.mx");
		when(userRepository.findById(adminCaller.getId())).thenReturn(Optional.of(adminCaller));
		when(userRepository.findByPersonId(personId)).thenReturn(Optional.empty());
		when(personRepository.findById(personId)).thenReturn(Optional.of(person));
		when(passwordHasher.hash("temp-password")).thenReturn("hashed-temp-pw");
		when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		UserCreationResult result = useCase
				.createUser(new CreateUserCommand(adminCaller.getId(), personId, "temp-password"));

		assertThat(result.username()).isEqualTo("jane.doe@utez.edu.mx");
		assertThat(result.mustChangePassword()).isTrue();

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-temp-pw");
	}

	@Test
	void createUser_rejectsDuplicateAccountForTheSamePerson() {
		UUID personId = UUID.randomUUID();
		when(userRepository.findById(adminCaller.getId())).thenReturn(Optional.of(adminCaller));
		when(userRepository.findByPersonId(personId))
				.thenReturn(Optional.of(new User(personId, "existing@utez.edu.mx", "hash")));

		assertThatThrownBy(
				() -> useCase.createUser(new CreateUserCommand(adminCaller.getId(), personId, "temp-password")))
				.isInstanceOf(PersonAlreadyHasUserException.class);
	}

	@Test
	void createUser_rejectsCreationWithoutInstitutionalEmail() {
		UUID personId = UUID.randomUUID();
		Person person = new Person("CURP000000HDFRRN01", "Jane", "Doe", null, null);
		when(userRepository.findById(adminCaller.getId())).thenReturn(Optional.of(adminCaller));
		when(userRepository.findByPersonId(personId)).thenReturn(Optional.empty());
		when(personRepository.findById(personId)).thenReturn(Optional.of(person));

		assertThatThrownBy(
				() -> useCase.createUser(new CreateUserCommand(adminCaller.getId(), personId, "temp-password")))
				.isInstanceOf(MissingInstitutionalEmailException.class);
	}

	@Test
	void createUser_mustChangePasswordCallerIsBlocked() {
		User blockedCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		when(userRepository.findById(blockedCaller.getId())).thenReturn(Optional.of(blockedCaller));

		assertThatThrownBy(() -> useCase
				.createUser(new CreateUserCommand(blockedCaller.getId(), UUID.randomUUID(), "temp-password")))
				.isInstanceOf(MustChangePasswordException.class);
	}
}
