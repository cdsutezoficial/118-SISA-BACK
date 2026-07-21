package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePeriodException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code AcademicPeriod}'s catalog fields. Revalidates
 * {@code (year, periodNumber)} uniqueness, excluding the record's own current
 * row — a self-update with an unchanged {@code (year, periodNumber)} must
 * succeed, same rule as {@code UpdateSubjectClassificationUseCaseImpl}'s
 * {@code code} revalidation. Date-range validation is enforced by
 * {@link AcademicPeriod#updateDetails} itself.
 */
public class UpdateAcademicPeriodUseCaseImpl implements UpdateAcademicPeriodUseCase {

	private final AcademicPeriodRepository periodRepository;

	public UpdateAcademicPeriodUseCaseImpl(AcademicPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public PeriodResult updatePeriod(UpdatePeriodCommand command) {
		AcademicPeriod period = periodRepository.findById(command.periodId())
				.orElseThrow(() -> new AcademicPeriodNotFoundException("Academic period not found: " + command.periodId()));

		periodRepository.findByYearAndPeriodNumber(command.year(), command.periodNumber())
				.filter(found -> !found.getId().equals(period.getId())).ifPresent(found -> {
					throw new DuplicatePeriodException("A period already exists for year " + command.year()
							+ " and periodNumber " + command.periodNumber());
				});

		period.updateDetails(command.name(), command.year(), command.periodNumber(), command.type(),
				command.startDate(), command.endDate(), command.enrollmentStart(), command.enrollmentEnd());
		AcademicPeriod saved = periodRepository.save(period);

		return CreateAcademicPeriodUseCaseImpl.toResult(saved);
	}
}
