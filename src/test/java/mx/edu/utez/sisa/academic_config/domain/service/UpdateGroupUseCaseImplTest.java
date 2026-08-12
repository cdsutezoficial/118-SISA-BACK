package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase.UpdateGroupCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import mx.edu.utez.sisa.shared.model.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateGroupUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private GroupRepository groupRepository;

	@Mock
	private GenerationRepository generationRepository;

	@Mock
	private AcademicPlanRepository planRepository;

	@Mock
	private AcademicPeriodRepository periodRepository;

	private UpdateGroupUseCaseImpl useCase;

	private UUID groupId;

	private UUID generationId;

	private UUID periodId;

	private UUID planId;

	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateGroupUseCaseImpl(groupRepository, generationRepository, planRepository, periodRepository);
		groupId = UUID.randomUUID();
		generationId = UUID.randomUUID();
		periodId = UUID.randomUUID();
		planId = UUID.randomUUID();
		programId = UUID.randomUUID();
	}

	@Test
	void updateGroup_successfulUpdateReResolvesProgramId() {
		Group existing = existingGroup();
		AcademicPlan plan = newPlan();
		UUID levelId = addLevel(plan, 4);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(existing));
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(newGeneration()));
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(groupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GroupResult result = useCase.updateGroup(
				new UpdateGroupCommand(groupId, generationId, periodId, levelId, "3B", 40, Shift.AFTERNOON));

		assertThat(result.code()).isEqualTo("3B");
		assertThat(result.maxCapacity()).isEqualTo(40);
		assertThat(result.shift()).isEqualTo(Shift.AFTERNOON);
		assertThat(result.programId()).isEqualTo(programId);
	}

	@Test
	void updateGroup_rejectsUnknownGroupId() {
		when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.updateGroup(new UpdateGroupCommand(groupId, generationId, periodId, UUID.randomUUID(), "3A", 35,
						Shift.MORNING)))
				.isInstanceOf(GroupNotFoundException.class);

		verify(groupRepository, never()).save(any());
	}

	@Test
	void updateGroup_rejectsNonExistentGenerationId() {
		Group existing = existingGroup();
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(existing));
		when(generationRepository.findById(generationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.updateGroup(new UpdateGroupCommand(groupId, generationId, periodId, UUID.randomUUID(), "3A", 35,
						Shift.MORNING)))
				.isInstanceOf(GenerationReferenceNotFoundException.class);

		verify(groupRepository, never()).save(any());
	}

	@Test
	void updateGroup_rejectsPlanLevelIdBelongingToDifferentPlan() {
		Group existing = existingGroup();
		AcademicPlan planOfGeneration = newPlan();
		addLevel(planOfGeneration, 1);
		AcademicPlan otherPlan = new AcademicPlan(UUID.randomUUID(), "2023-A", "Enero 2023", "TIT-002",
				LocalDate.of(2023, 1, 1), 6, BigDecimal.valueOf(6.0), 3, false, null);
		UUID levelOfOtherPlanId = addLevel(otherPlan, 1);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(existing));
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(newGeneration()));
		when(planRepository.findById(planId)).thenReturn(Optional.of(planOfGeneration));

		assertThatThrownBy(() -> useCase.updateGroup(
				new UpdateGroupCommand(groupId, generationId, periodId, levelOfOtherPlanId, "3A", 35, Shift.MORNING)))
				.isInstanceOf(PlanLevelNotFoundException.class);

		verify(groupRepository, never()).save(any());
	}

	@Test
	void updateGroup_rejectsNonExistentPeriodId() {
		Group existing = existingGroup();
		AcademicPlan plan = newPlan();
		UUID levelId = addLevel(plan, 1);
		when(groupRepository.findById(groupId)).thenReturn(Optional.of(existing));
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(newGeneration()));
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(periodRepository.findById(periodId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.updateGroup(new UpdateGroupCommand(groupId, generationId, periodId, levelId, "3A", 35, Shift.MORNING)))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(groupRepository, never()).save(any());
	}

	private Group existingGroup() {
		Group group = new Group(generationId, periodId, UUID.randomUUID(), programId, "3A", 35, Shift.MORNING);
		ReflectionTestUtils.setField(group, "id", groupId);
		return group;
	}

	private Generation newGeneration() {
		Generation generation = new Generation(planId, UUID.randomUUID(), programId, 7, 2026);
		ReflectionTestUtils.setField(generation, "id", generationId);
		return generation;
	}

	private AcademicPlan newPlan() {
		AcademicPlan plan = new AcademicPlan(programId, "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1),
				6, BigDecimal.valueOf(6.0), 3, false, null);
		ReflectionTestUtils.setField(plan, "id", planId);
		return plan;
	}

	private static UUID addLevel(AcademicPlan plan, int levelNumber) {
		PlanLevel level = plan.addLevel(levelNumber, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		return levelId;
	}

	private static AcademicPeriod newPeriod() {
		return new AcademicPeriod("Periodo 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
