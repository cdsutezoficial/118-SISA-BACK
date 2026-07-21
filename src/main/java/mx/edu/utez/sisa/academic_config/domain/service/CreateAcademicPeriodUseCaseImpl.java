package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePeriodException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an {@code AcademicPeriod} (plan: {@code docs/plans/2026-07-20-academic-period.md}).
 * Enforces {@code (year, periodNumber)} uniqueness only — {@code name} is
 * deliberately NOT validated for uniqueness, per the domain doc. Date-range
 * validation and the {@code CONFIGURATION} default status are enforced by
 * the {@link AcademicPeriod} constructor itself.
 */
public class CreateAcademicPeriodUseCaseImpl implements CreateAcademicPeriodUseCase {

	private final AcademicPeriodRepository periodRepository;

	public CreateAcademicPeriodUseCaseImpl(AcademicPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public PeriodResult createPeriod(CreatePeriodCommand command) {
		if (periodRepository.findByYearAndPeriodNumber(command.year(), command.periodNumber()).isPresent()) {
			throw new DuplicatePeriodException(
					"A period already exists for year " + command.year() + " and periodNumber " + command.periodNumber());
		}

		AcademicPeriod period = new AcademicPeriod(command.name(), command.year(), command.periodNumber(),
				command.type(), command.startDate(), command.endDate(), command.enrollmentStart(),
				command.enrollmentEnd());
		AcademicPeriod saved = periodRepository.save(period);

		return toResult(saved);
	}

	static PeriodResult toResult(AcademicPeriod period) {
		return new PeriodResult(period.getId(), period.getName(), period.getYear(), period.getPeriodNumber(),
				period.getType(), period.getStartDate(), period.getEndDate(), period.getEnrollmentStart(),
				period.getEnrollmentEnd(), period.getStatus());
	}
}
