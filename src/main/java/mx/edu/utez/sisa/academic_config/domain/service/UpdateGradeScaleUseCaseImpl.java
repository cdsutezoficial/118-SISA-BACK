package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScale;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces an existing {@code GradeScale}'s classification, range, and
 * entries in full (spec: "PUT /plans/{id}/grade-scales/{scaleId}"). Same
 * validation split as {@link SetGradeScaleUseCaseImpl}: {@code
 * classificationId} existence and the {@code numericMin < numericMax} range
 * here, not-found/duplicate-{@code classificationId}/coverage delegated to
 * {@link AcademicPlan#updateGradeScale}.
 */
public class UpdateGradeScaleUseCaseImpl implements UpdateGradeScaleUseCase {

	private final AcademicPlanRepository planRepository;

	private final SubjectClassificationRepository classificationRepository;

	public UpdateGradeScaleUseCaseImpl(AcademicPlanRepository planRepository,
			SubjectClassificationRepository classificationRepository) {
		this.planRepository = planRepository;
		this.classificationRepository = classificationRepository;
	}

	@Override
	@Transactional
	public GradeScaleResult updateGradeScale(UpdateGradeScaleCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));
		if (classificationRepository.findById(command.classificationId()).isEmpty()) {
			throw new ClassificationNotFoundException(
					"Subject classification not found: " + command.classificationId());
		}
		if (command.numericMin() == null || command.numericMax() == null
				|| command.numericMin().compareTo(command.numericMax()) >= 0) {
			throw new InvalidPlanDataException("La calificación mínima debe ser menor que la máxima: ["
					+ command.numericMin() + ", " + command.numericMax() + "].");
		}

		plan.updateGradeScale(command.scaleId(), command.classificationId(), command.numericMin(),
				command.numericMax(), SetGradeScaleUseCaseImpl.toEntryData(command.entries()));
		AcademicPlan saved = planRepository.save(plan);

		GradeScale updatedScale = saved.getGradeScales().stream()
				.filter(candidate -> command.scaleId().equals(candidate.getId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Updated grade scale disappeared: " + command.scaleId()));

		return CreateAcademicPlanUseCaseImpl.toGradeScaleResult(updatedScale);
	}
}
