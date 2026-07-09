package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddSubjectToPlanUseCase.AddSubjectCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateSubjectCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
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
class AddSubjectToPlanUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private AddSubjectToPlanUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private PlanLevel level;
	private UUID levelId;

	@BeforeEach
	void setUp() {
		useCase = new AddSubjectToPlanUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
		level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
	}

	@Test
	void addSubject_successfulAddition() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		UUID classificationId = UUID.randomUUID();

		SubjectResult result = useCase.addSubject(new AddSubjectCommand(planId, levelId, "MAT101", "Matematicas I", 5,
				4, 3, 1, SubjectType.CORE, true, classificationId));

		assertThat(result.code()).isEqualTo("MAT101");
		assertThat(result.classificationId()).isEqualTo(classificationId);
		assertThat(level.getSubjects()).hasSize(1);
	}

	@Test
	void addSubject_rejectsPlanLevelIdFromADifferentPlan() {
		AcademicPlan otherPlan = new AcademicPlan(UUID.randomUUID(), "2022-B", "Septiembre 2022", "TIT-002",
				LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null);
		PlanLevel levelOfOther = otherPlan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelOfOtherId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelOfOther, "id", levelOfOtherId);
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> useCase.addSubject(new AddSubjectCommand(planId, levelOfOtherId, "MAT101",
				"Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID())))
				.isInstanceOf(PlanLevelNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void addSubject_rejectsDuplicateCodeWithinTheSamePlan() {
		plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID());
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> useCase.addSubject(new AddSubjectCommand(planId, levelId, "MAT101", "Otra materia", 5,
				4, 3, 2, SubjectType.CORE, true, UUID.randomUUID())))
				.isInstanceOf(DuplicateSubjectCodeException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void addSubject_acceptsClassificationIdWithoutExistenceValidation() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		UUID nonExistentClassificationId = UUID.randomUUID();

		SubjectResult result = useCase.addSubject(new AddSubjectCommand(planId, levelId, "MAT101", "Matematicas I", 5,
				4, 3, 1, SubjectType.CORE, true, nonExistentClassificationId));

		assertThat(result.classificationId()).isEqualTo(nonExistentClassificationId);
	}

	@Test
	void addSubject_rejectsUnknownPlanId() {
		UUID unknownPlanId = UUID.randomUUID();
		when(planRepository.findById(unknownPlanId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.addSubject(new AddSubjectCommand(unknownPlanId, levelId, "MAT101",
				"Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID())))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}
}
