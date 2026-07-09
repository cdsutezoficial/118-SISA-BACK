package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Subject;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.SubjectNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code Subject} owned by an {@code AcademicPlan}
 * (spec: "Add and Update Subject"). Delegates the not-found and
 * duplicate-{@code code} checks to {@link AcademicPlan#updateSubject}.
 */
public class UpdateSubjectUseCaseImpl implements UpdateSubjectUseCase {

	private final AcademicPlanRepository planRepository;

	public UpdateSubjectUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public SubjectResult updateSubject(UpdateSubjectCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		plan.updateSubject(command.subjectId(), command.code(), command.name(), command.credits(),
				command.weeklyHours(), command.evaluationUnits(), command.displayOrder(), command.type(),
				command.isRetakeable(), command.classificationId());
		AcademicPlan saved = planRepository.save(plan);

		Subject updatedSubject = saved.getLevels().stream().flatMap(level -> level.getSubjects().stream())
				.filter(subject -> command.subjectId().equals(subject.getId())).findFirst()
				.orElseThrow(() -> new SubjectNotFoundException("Subject not found: " + command.subjectId()));

		return CreateAcademicPlanUseCaseImpl.toSubjectResult(updatedSubject);
	}
}
