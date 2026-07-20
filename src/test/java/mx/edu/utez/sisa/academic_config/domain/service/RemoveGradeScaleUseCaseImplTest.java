package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScale;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScaleEntryData;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase.RemoveGradeScaleCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GradeScaleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoveGradeScaleUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private RemoveGradeScaleUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private UUID scaleId;

	@BeforeEach
	void setUp() {
		useCase = new RemoveGradeScaleUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(new GradeScaleEntryData(BigDecimal.valueOf(0), BigDecimal.valueOf(100), "CO", "Competente",
						true)));
		scaleId = UUID.randomUUID();
		ReflectionTestUtils.setField(scale, "id", scaleId);
	}

	@Test
	void removeGradeScale_successfulRemoval() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.removeGradeScale(new RemoveGradeScaleCommand(planId, scaleId));

		assertThat(plan.getGradeScales()).isEmpty();
	}

	@Test
	void removeGradeScale_rejectsUnknownPlanId() {
		when(planRepository.findById(planId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.removeGradeScale(new RemoveGradeScaleCommand(planId, scaleId)))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void removeGradeScale_rejectsUnknownScaleId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		UUID unknownScaleId = UUID.randomUUID();

		assertThatThrownBy(() -> useCase.removeGradeScale(new RemoveGradeScaleCommand(planId, unknownScaleId)))
				.isInstanceOf(GradeScaleNotFoundException.class);

		verify(planRepository, never()).save(any());
	}
}
