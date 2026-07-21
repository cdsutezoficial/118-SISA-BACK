package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicPeriod} by id, 404 if missing — same
 * pattern as {@code GetSubjectClassificationUseCaseImpl}.
 */
public class GetAcademicPeriodUseCaseImpl implements GetAcademicPeriodUseCase {

	private final AcademicPeriodRepository periodRepository;

	public GetAcademicPeriodUseCaseImpl(AcademicPeriodRepository periodRepository) {
		this.periodRepository = periodRepository;
	}

	@Override
	public PeriodResult getById(UUID id) {
		AcademicPeriod period = periodRepository.findById(id)
				.orElseThrow(() -> new AcademicPeriodNotFoundException("Academic period not found: " + id));
		return CreateAcademicPeriodUseCaseImpl.toResult(period);
	}
}
