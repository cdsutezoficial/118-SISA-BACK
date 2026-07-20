package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScale;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScaleEntry;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.Subject;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePlanVersionException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidSocialServiceLevelException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Creates an {@code AcademicPlan} catalog entry (spec: "Create Academic
 * Plan"). Enforces {@code programId} existence via direct
 * {@link AcademicProgramRepository} injection (design.md — Decision: direct
 * same-module dependency, not a new port, mirroring
 * {@code CreateAcademicProgramUseCaseImpl}'s use of
 * {@code AcademicDivisionRepository}), {@code version} uniqueness within
 * {@code programId}, the {@code [0, 10]} range of {@code minPassingGrade},
 * and the creation-time {@code socialServiceMinLevelId} rule (spec: "no
 * PlanLevel can exist yet at creation time").
 */
public class CreateAcademicPlanUseCaseImpl implements CreateAcademicPlanUseCase {

	private static final BigDecimal MIN_PASSING_GRADE_FLOOR = BigDecimal.ZERO;
	private static final BigDecimal MIN_PASSING_GRADE_CEILING = BigDecimal.TEN;

	private final AcademicPlanRepository planRepository;
	private final AcademicProgramRepository programRepository;

	public CreateAcademicPlanUseCaseImpl(AcademicPlanRepository planRepository,
			AcademicProgramRepository programRepository) {
		this.planRepository = planRepository;
		this.programRepository = programRepository;
	}

	@Override
	@Transactional
	public AcademicPlanResult createPlan(CreateAcademicPlanCommand command) {
		if (command.programId() == null || programRepository.findById(command.programId()).isEmpty()) {
			throw new ProgramNotFoundException("Academic program not found: " + command.programId());
		}
		if (command.socialServiceMinLevelId() != null) {
			throw new InvalidSocialServiceLevelException(
					"socialServiceMinLevelId MUST be null at creation — no PlanLevel can exist yet");
		}
		if (command.minPassingGrade() == null || command.minPassingGrade().compareTo(MIN_PASSING_GRADE_FLOOR) < 0
				|| command.minPassingGrade().compareTo(MIN_PASSING_GRADE_CEILING) > 0) {
			throw new InvalidPlanDataException("minPassingGrade must be within [0, 10]: " + command.minPassingGrade());
		}
		if (planRepository.findByProgramIdAndVersion(command.programId(), command.version()).isPresent()) {
			throw new DuplicatePlanVersionException(
					"Plan version already in use for this program: " + command.version());
		}

		AcademicPlan plan = new AcademicPlan(command.programId(), command.version(), command.validityPeriod(),
				command.titulationKey(), command.effectiveFrom(), command.totalLevels(), command.minPassingGrade(),
				command.maxExtraordinaryExamsPerPeriod(), command.requiresSocialService(), null);
		AcademicPlan saved = planRepository.save(plan);

		return toResult(saved);
	}

	static AcademicPlanResult toResult(AcademicPlan plan) {
		return new AcademicPlanResult(plan.getId(), plan.getProgramId(), plan.getVersion(), plan.getValidityPeriod(),
				plan.getTitulationKey(), plan.getEffectiveFrom(), plan.getTotalLevels(), plan.getMinPassingGrade(),
				plan.getMaxExtraordinaryExamsPerPeriod(), plan.isRequiresSocialService(),
				plan.getSocialServiceMinLevelId(), plan.getStatus(),
				plan.getLevels().stream().map(CreateAcademicPlanUseCaseImpl::toLevelResult).toList(),
				plan.getGradeScales().stream().map(CreateAcademicPlanUseCaseImpl::toGradeScaleResult).toList());
	}

	/**
	 * Package-visible (not {@code private}) so the Phase 4 child-entity use
	 * cases ({@code AddPlanLevelUseCaseImpl}, {@code UpdatePlanLevelUseCaseImpl})
	 * can reuse it to build their {@code PlanLevelResult} return shape without
	 * duplicating the mapping.
	 */
	static PlanLevelResult toLevelResult(PlanLevel level) {
		return new PlanLevelResult(level.getId(), level.getLevelNumber(), level.getType(), level.getDescription(),
				level.getSubjects().stream().map(CreateAcademicPlanUseCaseImpl::toSubjectResult).toList());
	}

	/**
	 * Package-visible (not {@code private}) so the Phase 4 child-entity use
	 * cases ({@code AddSubjectToPlanUseCaseImpl}, {@code UpdateSubjectUseCaseImpl})
	 * can reuse it to build their {@code SubjectResult} return shape without
	 * duplicating the mapping.
	 */
	static SubjectResult toSubjectResult(Subject subject) {
		return new SubjectResult(subject.getId(), subject.getCode(), subject.getName(), subject.getCredits(),
				subject.getWeeklyHours(), subject.getEvaluationUnits(), subject.getDisplayOrder(), subject.getType(),
				subject.isRetakeable(), subject.getClassificationId());
	}

	/**
	 * Package-visible (not {@code private}) so the grade-scale use cases
	 * ({@code SetGradeScaleUseCaseImpl}, {@code UpdateGradeScaleUseCaseImpl})
	 * can reuse it to build their {@code GradeScaleResult} return shape
	 * without duplicating the mapping — mirrors {@link #toLevelResult}.
	 */
	static GradeScaleResult toGradeScaleResult(GradeScale scale) {
		return new GradeScaleResult(scale.getId(), scale.getClassificationId(), scale.getNumericMin(),
				scale.getNumericMax(),
				scale.getEntries().stream().map(CreateAcademicPlanUseCaseImpl::toGradeScaleEntryResult).toList());
	}

	static GradeScaleEntryResult toGradeScaleEntryResult(GradeScaleEntry entry) {
		return new GradeScaleEntryResult(entry.getId(), entry.getFromValue(), entry.getToValue(), entry.getLetter(),
				entry.getDescription(), entry.isPassed());
	}
}
