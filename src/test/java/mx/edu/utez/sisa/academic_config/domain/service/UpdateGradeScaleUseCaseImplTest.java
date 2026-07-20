package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScale;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScaleEntryData;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase.GradeScaleEntryCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGradeScaleUseCase.UpdateGradeScaleCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGradeScaleException;
import mx.edu.utez.sisa.academic_config.shared.exception.GradeScaleNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidGradeScaleEntriesException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
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
class UpdateGradeScaleUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	@Mock
	private SubjectClassificationRepository classificationRepository;

	private UpdateGradeScaleUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private UUID classificationId;
	private UUID scaleId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateGradeScaleUseCaseImpl(planRepository, classificationRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
		classificationId = UUID.randomUUID();
		GradeScale scale = plan.setGradeScale(classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(new GradeScaleEntryData(BigDecimal.valueOf(0), BigDecimal.valueOf(100), "CO", "Competente",
						true)));
		scaleId = UUID.randomUUID();
		ReflectionTestUtils.setField(scale, "id", scaleId);
	}

	@Test
	void updateGradeScale_successfulReplace() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(classificationRepository.findById(classificationId))
				.thenReturn(Optional.of(classification(classificationId)));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GradeScaleResult result = useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, scaleId,
				classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(10),
				List.of(entryCommand(0, 6, "NA", "No aprobado", false), entryCommand(7, 10, "AP", "Aprobado", true))));

		assertThat(result.numericMax()).isEqualByComparingTo(BigDecimal.valueOf(10));
		assertThat(result.entries()).hasSize(2);
	}

	@Test
	void updateGradeScale_rejectsUnknownPlanId() {
		when(planRepository.findById(planId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, scaleId,
				classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entryCommand(0, 100, "CO", "Competente", true)))))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateGradeScale_rejectsUnknownClassificationId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(classificationRepository.findById(classificationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, scaleId,
				classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entryCommand(0, 100, "CO", "Competente", true)))))
				.isInstanceOf(ClassificationNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateGradeScale_rejectsNumericMinGreaterThanOrEqualToNumericMax() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(classificationRepository.findById(classificationId))
				.thenReturn(Optional.of(classification(classificationId)));

		assertThatThrownBy(() -> useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, scaleId,
				classificationId, BigDecimal.valueOf(100), BigDecimal.valueOf(0),
				List.of(entryCommand(0, 100, "CO", "Competente", true)))))
				.isInstanceOf(InvalidPlanDataException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateGradeScale_rejectsUnknownScaleId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(classificationRepository.findById(classificationId))
				.thenReturn(Optional.of(classification(classificationId)));
		UUID unknownScaleId = UUID.randomUUID();

		assertThatThrownBy(() -> useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, unknownScaleId,
				classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entryCommand(0, 100, "CO", "Competente", true)))))
				.isInstanceOf(GradeScaleNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateGradeScale_rejectsDuplicateClassificationAgainstAnotherScale() {
		UUID otherClassificationId = UUID.randomUUID();
		plan.setGradeScale(otherClassificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(10),
				List.of(new GradeScaleEntryData(BigDecimal.valueOf(0), BigDecimal.valueOf(10), "AP", "Aprobado",
						true)));
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(classificationRepository.findById(otherClassificationId))
				.thenReturn(Optional.of(classification(otherClassificationId)));

		assertThatThrownBy(() -> useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, scaleId,
				otherClassificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entryCommand(0, 100, "CO", "Competente", true)))))
				.isInstanceOf(DuplicateGradeScaleException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateGradeScale_rejectsEntriesWithAGap() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(classificationRepository.findById(classificationId))
				.thenReturn(Optional.of(classification(classificationId)));

		assertThatThrownBy(() -> useCase.updateGradeScale(new UpdateGradeScaleCommand(planId, scaleId,
				classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entryCommand(0, 60, "NP", "No competente", false), entryCommand(70, 100, "CO", "Competente",
						true))))).isInstanceOf(InvalidGradeScaleEntriesException.class);

		verify(planRepository, never()).save(any());
	}

	private static GradeScaleEntryCommand entryCommand(int from, int to, String letter, String description,
			boolean passed) {
		return new GradeScaleEntryCommand(BigDecimal.valueOf(from), BigDecimal.valueOf(to), letter, description,
				passed);
	}

	private static SubjectClassification classification(UUID id) {
		SubjectClassification classification = new SubjectClassification("Competente", "COMP");
		ReflectionTestUtils.setField(classification, "id", id);
		return classification;
	}
}
