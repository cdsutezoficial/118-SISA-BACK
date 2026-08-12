package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase.CreatePersonCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase.PersonResult;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateCurpException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePersonUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PersonRepository personRepository;

	private CreatePersonUseCaseImpl useCase;

	private User adminCaller;
	private UUID callerId;

	@BeforeEach
	void setUp() {
		useCase = new CreatePersonUseCaseImpl(userRepository, personRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2"); // clears mustChangePassword so the caller can operate
		callerId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
	}

	@Test
	void createPerson_successfulCreation() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.findByCurp("CURP000000HDFRRN01")).thenReturn(Optional.empty());
		when(personRepository.findByInstitutionalEmail("jane.doe@utez.edu.mx")).thenReturn(Optional.empty());
		when(personRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PersonResult result = useCase.createPerson(new CreatePersonCommand(callerId, "CURP000000HDFRRN01", "Jane",
				"Doe", null, "jane.doe@utez.edu.mx"));

		assertThat(result.curp()).isEqualTo("CURP000000HDFRRN01");
		assertThat(result.firstName()).isEqualTo("Jane");
		assertThat(result.institutionalEmail()).isEqualTo("jane.doe@utez.edu.mx");

		ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
		verify(personRepository).save(captor.capture());
		assertThat(captor.getValue().getCurp()).isEqualTo("CURP000000HDFRRN01");
	}

	@Test
	void createPerson_rejectsDuplicateCurp() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.findByCurp("CURP000000HDFRRN01"))
				.thenReturn(Optional.of(new Person("CURP000000HDFRRN01", "Existing", "Person", null, "existing@utez.edu.mx")));

		assertThatThrownBy(() -> useCase.createPerson(new CreatePersonCommand(callerId, "CURP000000HDFRRN01", "Jane",
				"Doe", null, "jane.doe@utez.edu.mx"))).isInstanceOf(DuplicateCurpException.class);
	}

	@Test
	void createPerson_rejectsDuplicateInstitutionalEmail() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.findByCurp("CURP000000HDFRRN02")).thenReturn(Optional.empty());
		when(personRepository.findByInstitutionalEmail("jane.doe@utez.edu.mx"))
				.thenReturn(Optional.of(new Person("CURP999999HDFRRN99", "Other", "Person", null, "jane.doe@utez.edu.mx")));

		assertThatThrownBy(() -> useCase.createPerson(new CreatePersonCommand(callerId, "CURP000000HDFRRN02", "Jane",
				"Doe", null, "jane.doe@utez.edu.mx"))).isInstanceOf(DuplicateInstitutionalEmailException.class);
	}

	@Test
	void createPerson_callerNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.createPerson(new CreatePersonCommand(callerId, "CURP000000HDFRRN03", "Jane",
				"Doe", null, "jane.doe2@utez.edu.mx"))).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void createPerson_mustChangePasswordCallerIsBlocked() {
		User blockedCaller = new User(UUID.randomUUID(), "admin2@utez.edu.mx", "hashed-admin-pw");
		UUID blockedCallerId = UUID.randomUUID();
		ReflectionTestUtils.setField(blockedCaller, "id", blockedCallerId);
		when(userRepository.findById(blockedCallerId)).thenReturn(Optional.of(blockedCaller));

		assertThatThrownBy(() -> useCase.createPerson(new CreatePersonCommand(blockedCallerId, "CURP000000HDFRRN04",
				"Jane", "Doe", null, "jane.doe3@utez.edu.mx"))).isInstanceOf(MustChangePasswordException.class);
	}
}
