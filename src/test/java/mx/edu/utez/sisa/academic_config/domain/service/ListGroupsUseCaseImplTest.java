package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.ListGroupsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.ListGroupsResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository.GroupSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository.GroupSearchPage;
import mx.edu.utez.sisa.shared.model.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListGroupsUseCaseImplTest {

	@Mock
	private GroupRepository groupRepository;

	private ListGroupsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListGroupsUseCaseImpl(groupRepository);
	}

	@Test
	void listGroups_mapsPageContentToSummaries() {
		Group group = new Group(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "3A", 35,
				Shift.MORNING);
		when(groupRepository.search(new GroupSearchCriteria(null, null, null, null, 0, 20)))
				.thenReturn(new GroupSearchPage(List.of(group), 1L, 1));

		ListGroupsResult result = useCase.listGroups(new ListGroupsQuery(null, null, null, null, 0, 20));

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).code()).isEqualTo("3A");
		assertThat(result.totalElements()).isEqualTo(1L);
	}

	@Test
	void listGroups_passesProgramIdAndGenerationIdFiltersThrough() {
		UUID programId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		when(groupRepository
				.search(new GroupSearchCriteria(GroupStatus.OPEN, "3A", programId, generationId, 0, 20)))
				.thenReturn(new GroupSearchPage(List.of(), 0L, 0));

		useCase.listGroups(new ListGroupsQuery(GroupStatus.OPEN, "3A", programId, generationId, 0, 20));

		// Verified via the stub above matching exact criteria — Mockito throws
		// an UnnecessaryStubbingException at verification time if the call
		// shape ever drifts from what listGroups actually passes through.
	}

	@Test
	void listGroups_negativePageNormalizesToZero() {
		when(groupRepository.search(new GroupSearchCriteria(null, null, null, null, 0, 20)))
				.thenReturn(new GroupSearchPage(List.of(), 0L, 0));

		ListGroupsResult result = useCase.listGroups(new ListGroupsQuery(null, null, null, null, -5, 20));

		assertThat(result.page()).isZero();
	}

	@Test
	void listGroups_nonPositiveSizeFallsBackToDefault() {
		when(groupRepository.search(new GroupSearchCriteria(null, null, null, null, 0, 20)))
				.thenReturn(new GroupSearchPage(List.of(), 0L, 0));

		ListGroupsResult result = useCase.listGroups(new ListGroupsQuery(null, null, null, null, 0, 0));

		assertThat(result.size()).isEqualTo(20);
	}

	@Test
	void listGroups_oversizedSizeIsCappedAtMax() {
		when(groupRepository.search(new GroupSearchCriteria(null, null, null, null, 0, 100)))
				.thenReturn(new GroupSearchPage(List.of(), 0L, 0));

		ListGroupsResult result = useCase.listGroups(new ListGroupsQuery(null, null, null, null, 0, 500));

		assertThat(result.size()).isEqualTo(100);
	}
}
