package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAcademicPlanUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private GetAcademicPlanUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;

	@BeforeEach
	void setUp() {
		useCase = new GetAcademicPlanUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1),
				6, BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
	}

	@Test
	void getById_returnsThePlanWithItsLevelsAndSubjects() {
		PlanLevel levelOne = plan.addLevel(1, PlanLevelType.REGULAR, null);
		ReflectionTestUtils.setField(levelOne, "id", UUID.randomUUID());
		plan.addLevel(2, PlanLevelType.REGULAR, null);
		plan.addSubject(levelOne.getId(), "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				UUID.randomUUID());
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		AcademicPlanResult result = useCase.getById(planId);

		assertThat(result.id()).isEqualTo(planId);
		assertThat(result.levels()).hasSize(2);
		assertThat(result.levels().get(0).subjects()).extracting("code").containsExactly("MAT101");
	}

	@Test
	void getById_rejectsUnknownPlanId() {
		UUID unknownId = UUID.randomUUID();
		when(planRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(AcademicPlanNotFoundException.class);
	}
}
