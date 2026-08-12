package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;
import mx.edu.utez.sisa.shared.model.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeGroupStatusUseCaseImplTest {

	@Mock
	private GroupRepository groupRepository;

	private ChangeGroupStatusUseCaseImpl useCase;

	private UUID groupId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeGroupStatusUseCaseImpl(groupRepository);
		groupId = UUID.randomUUID();
	}

	@Test
	void changeStatus_toClosedSucceeds() {
		Group group = new Group(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "3A", 35,
				Shift.MORNING);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(groupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GroupResult result = useCase.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), groupId, GroupStatus.CLOSED));

		assertThat(result.status()).isEqualTo(GroupStatus.CLOSED);
	}

	@Test
	void changeStatus_backToOpenSucceeds() {
		// Both directions are always valid — unlike AcademicPeriod's strict
		// sequence, there is no "terminal" state here.
		Group group = new Group(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "3A", 35,
				Shift.MORNING);
		group.close();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
		when(groupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GroupResult result = useCase.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), groupId, GroupStatus.OPEN));

		assertThat(result.status()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void changeStatus_throwsWhenGroupNotFound() {
		when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), groupId, GroupStatus.CLOSED)))
				.isInstanceOf(GroupNotFoundException.class);
	}
}
