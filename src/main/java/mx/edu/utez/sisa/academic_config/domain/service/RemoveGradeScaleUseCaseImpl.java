package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a {@code GradeScale} owned by an {@code AcademicPlan} (spec:
 * "DELETE /plans/{id}/grade-scales/{scaleId}"). Delegates the not-found check
 * to {@link AcademicPlan#removeGradeScale}.
 */
public class RemoveGradeScaleUseCaseImpl implements RemoveGradeScaleUseCase {

	private final AcademicPlanRepository planRepository;

	public RemoveGradeScaleUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public void removeGradeScale(RemoveGradeScaleCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		plan.removeGradeScale(command.scaleId());
		planRepository.save(plan);
	}
}
