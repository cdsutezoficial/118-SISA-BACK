package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetGroupUseCaseImplTest {

	@Mock
	private GroupRepository groupRepository;

	private GetGroupUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new GetGroupUseCaseImpl(groupRepository);
	}

	@Test
	void getById_returnsResultWhenFound() {
		UUID groupId = UUID.randomUUID();
		Group group = new Group(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "3A", 35,
				Shift.MORNING);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

		GroupResult result = useCase.getById(groupId);

		assertThat(result.code()).isEqualTo("3A");
	}

	@Test
	void getById_throwsWhenNotFound() {
		UUID unknownId = UUID.randomUUID();
		when(groupRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(GroupNotFoundException.class);
	}
}
