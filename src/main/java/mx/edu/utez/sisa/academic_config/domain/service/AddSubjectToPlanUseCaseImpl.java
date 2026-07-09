package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Subject;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddSubjectToPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a {@code Subject} to a {@code PlanLevel} of an existing
 * {@code AcademicPlan} (spec: "Add and Update Subject"). Delegates the
 * cross-plan {@code planLevelId} check and the duplicate-{@code code} check
 * to {@link AcademicPlan#addSubject}. {@code classificationId} is accepted
 * without existence validation (spec: "classificationId is accepted without
 * existence validation").
 */
public class AddSubjectToPlanUseCaseImpl implements AddSubjectToPlanUseCase {

	private final AcademicPlanRepository planRepository;

	public AddSubjectToPlanUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public SubjectResult addSubject(AddSubjectCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		plan.addSubject(command.planLevelId(), command.code(), command.name(), command.credits(),
				command.weeklyHours(), command.evaluationUnits(), command.displayOrder(), command.type(),
				command.isRetakeable(), command.classificationId());
		AcademicPlan saved = planRepository.save(plan);

		/*
		 * Re-fetched from the SAVED plan by code (unique within the plan),
		 * not the in-memory reference returned by addSubject() above — same
		 * apply-phase discovery/rationale as AddPlanLevelUseCaseImpl.
		 */
		Subject savedSubject = saved.getLevels().stream().flatMap(level -> level.getSubjects().stream())
				.filter(candidate -> candidate.getCode().equals(command.code())).findFirst()
				.orElseThrow(() -> new IllegalStateException("Added subject disappeared: " + command.code()));

		return CreateAcademicPlanUseCaseImpl.toSubjectResult(savedSubject);
	}
}
