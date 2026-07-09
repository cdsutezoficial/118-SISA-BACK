package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.Subject;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectUseCase.UpdateSubjectCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.SubjectNotFoundException;
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
class UpdateSubjectUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private UpdateSubjectUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private PlanLevel level;
	private UUID levelId;
	private Subject subject;
	private UUID subjectId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateSubjectUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
		level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		subject = plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				UUID.randomUUID());
		subjectId = UUID.randomUUID();
		ReflectionTestUtils.setField(subject, "id", subjectId);
	}

	@Test
	void updateSubject_successfulUpdate() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		SubjectResult result = useCase.updateSubject(new UpdateSubjectCommand(planId, subjectId, "MAT101",
				"Matematicas I Actualizada", 6, 5, 4, 1, SubjectType.CORE, false, UUID.randomUUID()));

		assertThat(result.name()).isEqualTo("Matematicas I Actualizada");
		assertThat(result.credits()).isEqualTo(6);
		assertThat(result.isRetakeable()).isFalse();
	}

	@Test
	void updateSubject_rejectsUnknownSubjectId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		UUID unknownSubjectId = UUID.randomUUID();

		assertThatThrownBy(() -> useCase.updateSubject(new UpdateSubjectCommand(planId, unknownSubjectId, "MAT101",
				"Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID())))
				.isInstanceOf(SubjectNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateSubject_rejectsUnknownPlanId() {
		UUID unknownPlanId = UUID.randomUUID();
		when(planRepository.findById(unknownPlanId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateSubject(new UpdateSubjectCommand(unknownPlanId, subjectId, "MAT101",
				"Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID())))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}
}
