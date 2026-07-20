package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScale;
import mx.edu.utez.sisa.academic_config.domain.model.GradeScaleEntryData;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase.GradeScaleEntryCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Defines a {@code GradeScale} for an existing {@code AcademicPlan} +
 * classification in a single "set complete" operation (spec:
 * "SetGradeScaleUseCase"). Enforces {@code classificationId} existence via
 * direct {@link SubjectClassificationRepository} injection (design.md —
 * Decision: direct same-module dependency, not a new port, mirrors
 * {@code CreateAcademicPlanUseCaseImpl}'s use of
 * {@code AcademicProgramRepository}) and the {@code numericMin <
 * numericMax} range at this layer; {@code classificationId} uniqueness
 * within the plan and entry coverage/gap/overlap are delegated to
 * {@link AcademicPlan#setGradeScale}.
 */
public class SetGradeScaleUseCaseImpl implements SetGradeScaleUseCase {

	private final AcademicPlanRepository planRepository;

	private final SubjectClassificationRepository classificationRepository;

	public SetGradeScaleUseCaseImpl(AcademicPlanRepository planRepository,
			SubjectClassificationRepository classificationRepository) {
		this.planRepository = planRepository;
		this.classificationRepository = classificationRepository;
	}

	@Override
	@Transactional
	public GradeScaleResult setGradeScale(SetGradeScaleCommand command) {
		AcademicPlan plan = planRepository.findById(command.planId())
				.orElseThrow(() -> new AcademicPlanNotFoundException("Academic plan not found: " + command.planId()));
		if (classificationRepository.findById(command.classificationId()).isEmpty()) {
			throw new ClassificationNotFoundException(
					"Subject classification not found: " + command.classificationId());
		}
		if (command.numericMin() == null || command.numericMax() == null
				|| command.numericMin().compareTo(command.numericMax()) >= 0) {
			throw new InvalidPlanDataException(
					"numericMin must be less than numericMax: [" + command.numericMin() + ", " + command.numericMax()
							+ "]");
		}

		plan.setGradeScale(command.classificationId(), command.numericMin(), command.numericMax(),
				toEntryData(command.entries()));
		AcademicPlan saved = planRepository.save(plan);

		/*
		 * Re-fetched from the SAVED plan by classificationId (unique within
		 * the plan), not the in-memory reference returned by setGradeScale()
		 * above — same apply-phase discovery as AddPlanLevelUseCaseImpl.
		 */
		GradeScale savedScale = saved.getGradeScales().stream()
				.filter(candidate -> command.classificationId().equals(candidate.getClassificationId())).findFirst()
				.orElseThrow(
						() -> new IllegalStateException("Added grade scale disappeared: " + command.classificationId()));

		return CreateAcademicPlanUseCaseImpl.toGradeScaleResult(savedScale);
	}

	static List<GradeScaleEntryData> toEntryData(List<GradeScaleEntryCommand> entries) {
		return entries.stream()
				.map(entry -> new GradeScaleEntryData(entry.fromValue(), entry.toValue(), entry.letter(),
						entry.description(), entry.passed()))
				.toList();
	}
}
