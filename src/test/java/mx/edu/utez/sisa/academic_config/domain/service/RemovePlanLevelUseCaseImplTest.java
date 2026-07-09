package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemovePlanLevelUseCase.RemovePlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelHasSubjectsException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelInUseException;
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
class RemovePlanLevelUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private RemovePlanLevelUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private PlanLevel level;
	private UUID levelId;

	@BeforeEach
	void setUp() {
		useCase = new RemovePlanLevelUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
		level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
	}

	@Test
	void removeLevel_successfulRemoval() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.removeLevel(new RemovePlanLevelCommand(planId, levelId));

		assertThat(plan.getLevels()).isEmpty();
	}

	@Test
	void removeLevel_rejectsWhenLevelIsSocialServiceMinLevel() {
		ReflectionTestUtils.setField(plan, "socialServiceMinLevelId", levelId);
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> useCase.removeLevel(new RemovePlanLevelCommand(planId, levelId)))
				.isInstanceOf(PlanLevelInUseException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void removeLevel_rejectsWhenLevelStillHasSubjects() {
		plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID());
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> useCase.removeLevel(new RemovePlanLevelCommand(planId, levelId)))
				.isInstanceOf(PlanLevelHasSubjectsException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void removeLevel_rejectsUnknownPlanId() {
		UUID unknownPlanId = UUID.randomUUID();
		when(planRepository.findById(unknownPlanId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.removeLevel(new RemovePlanLevelCommand(unknownPlanId, levelId)))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}
}
