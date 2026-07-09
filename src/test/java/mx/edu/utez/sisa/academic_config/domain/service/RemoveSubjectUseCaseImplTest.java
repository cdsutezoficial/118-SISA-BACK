package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.Subject;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveSubjectUseCase.RemoveSubjectCommand;
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
class RemoveSubjectUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private RemoveSubjectUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private PlanLevel level;
	private UUID levelId;
	private Subject subject;
	private UUID subjectId;

	@BeforeEach
	void setUp() {
		useCase = new RemoveSubjectUseCaseImpl(planRepository);
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
	void removeSubject_removesItFromItsLevel() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.removeSubject(new RemoveSubjectCommand(planId, subjectId));

		assertThat(level.getSubjects()).isEmpty();
	}

	@Test
	void removeSubject_rejectsUnknownSubjectId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		UUID unknownSubjectId = UUID.randomUUID();

		assertThatThrownBy(() -> useCase.removeSubject(new RemoveSubjectCommand(planId, unknownSubjectId)))
				.isInstanceOf(SubjectNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void removeSubject_rejectsUnknownPlanId() {
		UUID unknownPlanId = UUID.randomUUID();
		when(planRepository.findById(unknownPlanId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.removeSubject(new RemoveSubjectCommand(unknownPlanId, subjectId)))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}
}
