package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.ListPersonsQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.ListPersonsResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.PersonSummary;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository.PersonSearchCriteria;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository.PersonSearchPage;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPersonsUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PersonRepository personRepository;

	private ListPersonsUseCaseImpl useCase;

	private User adminCaller;
	private UUID callerId;

	@BeforeEach
	void setUp() {
		useCase = new ListPersonsUseCaseImpl(userRepository, personRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2");
		callerId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
	}

	@Test
	void listPersons_callerNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.listPersons(new ListPersonsQuery(callerId, null, 0, 20)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void listPersons_callerWithPendingPasswordChangeIsRejected() {
		User pendingCaller = new User(UUID.randomUUID(), "pending@utez.edu.mx", "hash");
		UUID pendingCallerId = UUID.randomUUID();
		ReflectionTestUtils.setField(pendingCaller, "id", pendingCallerId);
		when(userRepository.findById(pendingCallerId)).thenReturn(Optional.of(pendingCaller));

		assertThatThrownBy(() -> useCase.listPersons(new ListPersonsQuery(pendingCallerId, null, 0, 20)))
				.isInstanceOf(MustChangePasswordException.class);
	}

	@Test
	void listPersons_marksHasUserTrueWhenPersonHasLinkedUser() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID personId = UUID.randomUUID();
		Person person = new Person("CURP123456789012", "Ana", "García", "López", "ana@utez.edu.mx");
		ReflectionTestUtils.setField(person, "id", personId);
		when(personRepository.search(any())).thenReturn(new PersonSearchPage(List.of(person), 1L, 1));
		when(userRepository.findByPersonId(personId))
				.thenReturn(Optional.of(new User(personId, "ana@utez.edu.mx", "hash")));

		ListPersonsResult result = useCase.listPersons(new ListPersonsQuery(callerId, null, 0, 20));

		assertThat(result.persons()).hasSize(1);
		PersonSummary summary = result.persons().get(0);
		assertThat(summary.hasUser()).isTrue();
		assertThat(summary.curp()).isEqualTo("CURP123456789012");
	}

	@Test
	void listPersons_marksHasUserFalseWhenPersonHasNoLinkedUser() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID personId = UUID.randomUUID();
		Person person = new Person("CURP223456789012", "Juan", "Pérez", null, "juan@utez.edu.mx");
		ReflectionTestUtils.setField(person, "id", personId);
		when(personRepository.search(any())).thenReturn(new PersonSearchPage(List.of(person), 1L, 1));
		when(userRepository.findByPersonId(personId)).thenReturn(Optional.empty());

		ListPersonsResult result = useCase.listPersons(new ListPersonsQuery(callerId, null, 0, 20));

		assertThat(result.persons().get(0).hasUser()).isFalse();
	}

	@Test
	void listPersons_passesSearchThroughToRepository() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.search(any())).thenReturn(new PersonSearchPage(List.of(), 0L, 0));

		useCase.listPersons(new ListPersonsQuery(callerId, "ana", 2, 15));

		ArgumentCaptor<PersonSearchCriteria> captor = ArgumentCaptor.forClass(PersonSearchCriteria.class);
		verify(personRepository).search(captor.capture());
		assertThat(captor.getValue().search()).isEqualTo("ana");
		assertThat(captor.getValue().page()).isEqualTo(2);
		assertThat(captor.getValue().size()).isEqualTo(15);
	}

	@Test
	void listPersons_normalizesNegativePageToZero() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.search(any())).thenReturn(new PersonSearchPage(List.of(), 0L, 0));

		useCase.listPersons(new ListPersonsQuery(callerId, null, -5, 20));

		ArgumentCaptor<PersonSearchCriteria> captor = ArgumentCaptor.forClass(PersonSearchCriteria.class);
		verify(personRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isEqualTo(0);
	}

	@Test
	void listPersons_normalizesNonPositiveSizeToDefault() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.search(any())).thenReturn(new PersonSearchPage(List.of(), 0L, 0));

		useCase.listPersons(new ListPersonsQuery(callerId, null, 0, 0));

		ArgumentCaptor<PersonSearchCriteria> captor = ArgumentCaptor.forClass(PersonSearchCriteria.class);
		verify(personRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListPersonsQuery.DEFAULT_PAGE_SIZE);
	}

	@Test
	void listPersons_capsOversizedSizeAtMaximum() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(personRepository.search(any())).thenReturn(new PersonSearchPage(List.of(), 0L, 0));

		useCase.listPersons(new ListPersonsQuery(callerId, null, 0, 5000));

		ArgumentCaptor<PersonSearchCriteria> captor = ArgumentCaptor.forClass(PersonSearchCriteria.class);
		verify(personRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListPersonsQuery.MAX_PAGE_SIZE);
	}
}
