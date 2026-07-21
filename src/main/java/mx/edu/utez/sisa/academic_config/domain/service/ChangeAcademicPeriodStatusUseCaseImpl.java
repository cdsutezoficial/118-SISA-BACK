package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transitions an {@code AcademicPeriod}'s status. The actual sequential
 * forward-only validation lives in {@link AcademicPeriod#changeStatus} — this
 * use case is a thin find-then-mutate-then-save shell, mirroring
 * {@code ChangeSubjectClassificationStatusUseCaseImpl}.
 */
public class ChangeAcademicPeriodStatusUseCaseImpl implements ChangeAcademicPeriodStatusUseCase {

	private final AcademicPeriodRepository periodRepository;

	public ChangeAcademicPeriodStatusUseCaseImpl(AcademicPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public PeriodResult changeStatus(ChangeStatusCommand command) {
		AcademicPeriod period = periodRepository.findById(command.periodId())
				.orElseThrow(() -> new AcademicPeriodNotFoundException("Academic period not found: " + command.periodId()));

		period.changeStatus(command.target());
		AcademicPeriod saved = periodRepository.save(period);

		return CreateAcademicPeriodUseCaseImpl.toResult(saved);
	}
}
