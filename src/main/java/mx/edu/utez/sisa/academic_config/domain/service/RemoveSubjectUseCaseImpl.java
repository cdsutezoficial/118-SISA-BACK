package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a {@code Subject} owned by an {@code AcademicPlan} (spec: "Add and
 * Update Subject"). Delegates the not-found check to
 * {@link AcademicPlan#removeSubject}, which removes the subject from
 * whichever {@code PlanLevel} owns it.
 */
public class RemoveSubjectUseCaseImpl implements RemoveSubjectUseCase {

	private final AcademicPlanRepository planRepository;

	public RemoveSubjectUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public void removeSubject(RemoveSubjectCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		plan.removeSubject(command.subjectId());
		planRepository.save(plan);
	}
}
