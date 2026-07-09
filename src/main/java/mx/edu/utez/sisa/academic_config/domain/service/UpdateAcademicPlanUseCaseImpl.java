package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePlanVersionException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidSocialServiceLevelException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Updates an existing {@code AcademicPlan}'s catalog fields (spec: "Update
 * Academic Plan"). Applies the same {@code version} uniqueness-within-
 * {@code programId} rule as creation, except a plan's own current value
 * never counts as a conflict against itself, plus the
 * {@code socialServiceMinLevelId} ownership rule (design.md — validated via
 * {@link AcademicPlan#hasLevel(java.util.UUID)}: "Rejects
 * socialServiceMinLevelId referencing a PlanLevel from a different plan").
 * Also enforces the {@code [0, 10]} range of {@code minPassingGrade}
 * (post-verify fast-follow bugfix — {@code CreateAcademicPlanUseCaseImpl}
 * enforced this at creation, but the same field is updatable per spec's
 * "Update Academic Plan" requirement and had no equivalent guard here,
 * silently accepting/persisting an out-of-range value).
 */
public class UpdateAcademicPlanUseCaseImpl implements UpdateAcademicPlanUseCase {

	private static final BigDecimal MIN_PASSING_GRADE_FLOOR = BigDecimal.ZERO;
	private static final BigDecimal MIN_PASSING_GRADE_CEILING = BigDecimal.TEN;

	private final AcademicPlanRepository planRepository;

	public UpdateAcademicPlanUseCaseImpl(AcademicPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	@Transactional
	public AcademicPlanResult updatePlan(UpdateAcademicPlanCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));

		if (command.minPassingGrade() == null || command.minPassingGrade().compareTo(MIN_PASSING_GRADE_FLOOR) < 0
				|| command.minPassingGrade().compareTo(MIN_PASSING_GRADE_CEILING) > 0) {
			throw new InvalidPlanDataException("minPassingGrade must be within [0, 10]: " + command.minPassingGrade());
		}

		planRepository.findByProgramIdAndVersion(plan.getProgramId(), command.version())
				.filter(found -> !found.getId().equals(plan.getId())).ifPresent(found -> {
					throw new DuplicatePlanVersionException(
							"Plan version already in use for this program: " + command.version());
				});
		if (command.requiresSocialService() && command.socialServiceMinLevelId() != null
				&& !plan.hasLevel(command.socialServiceMinLevelId())) {
			throw new InvalidSocialServiceLevelException(
					"socialServiceMinLevelId does not belong to this plan: " + command.socialServiceMinLevelId());
		}

		plan.updateDetails(command.version(), command.validityPeriod(), command.titulationKey(),
				command.effectiveFrom(), command.totalLevels(), command.minPassingGrade(),
				command.maxExtraordinaryExamsPerPeriod(), command.requiresSocialService(),
				command.socialServiceMinLevelId());
		AcademicPlan saved = planRepository.save(plan);

		return CreateAcademicPlanUseCaseImpl.toResult(saved);
	}
}
