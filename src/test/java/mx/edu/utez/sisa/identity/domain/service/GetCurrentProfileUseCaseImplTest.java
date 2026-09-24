package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase.CurrentProfileQuery;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase.CurrentProfileResult;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentProfileUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PersonRepository personRepository;

	private GetCurrentProfileUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new GetCurrentProfileUseCaseImpl(userRepository, personRepository);
	}

	@Test
	void getCurrentProfile_unknownUserThrowsUserNotFound() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getCurrentProfile(new CurrentProfileQuery(userId)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getCurrentProfile_joinsPersonFullNameAndPrefersInstitutionalEmail() {
		UUID userId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User user = new User(personId, "jane.doe@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		Person person = new Person("CURP123456789012", "Jane", "Doe", "Smith", "jane.doe@utez.edu.mx");
		person.setPersonalEmail("jane@gmail.com");
		ReflectionTestUtils.setField(person, "id", personId);
		when(personRepository.findById(personId)).thenReturn(Optional.of(person));

		CurrentProfileResult result = useCase.getCurrentProfile(new CurrentProfileQuery(userId));

		assertThat(result.userId()).isEqualTo(userId);
		assertThat(result.fullName()).isEqualTo("Jane Doe Smith");
		assertThat(result.username()).isEqualTo("jane.doe@utez.edu.mx");
		assertThat(result.email()).isEqualTo("jane.doe@utez.edu.mx");
	}

	@Test
	void getCurrentProfile_missingInstitutionalEmailFallsBackToPersonalThenUsername() {
		UUID userId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User user = new User(personId, "user.with.curp", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		Person person = new Person("CURP123456789012", "Ana", "García", null, null);
		person.setPersonalEmail("ana.garcia@gmail.com");
		ReflectionTestUtils.setField(person, "id", personId);
		when(personRepository.findById(personId)).thenReturn(Optional.of(person));

		CurrentProfileResult personalEmail = useCase.getCurrentProfile(new CurrentProfileQuery(userId));
		assertThat(personalEmail.email()).isEqualTo("ana.garcia@gmail.com");

		person.setPersonalEmail(null);
		CurrentProfileResult usernameFallback = useCase.getCurrentProfile(new CurrentProfileQuery(userId));
		assertThat(usernameFallback.email()).isEqualTo("user.with.curp");
	}

	@Test
	void getCurrentProfile_missingPersonReturnsBlankFullNameButUsernameEmail() {
		UUID userId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User user = new User(personId, "orphan@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(user, "id", userId);
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(personRepository.findById(personId)).thenReturn(Optional.empty());

		CurrentProfileResult result = useCase.getCurrentProfile(new CurrentProfileQuery(userId));

		assertThat(result.fullName()).isEmpty();
		assertThat(result.email()).isEqualTo("orphan@utez.edu.mx");
	}
}