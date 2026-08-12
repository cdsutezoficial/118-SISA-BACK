package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code HighSchoolType} by id, 404 if missing — same
 * pattern as {@code GetOutreachChannelUseCaseImpl}.
 */
public class GetHighSchoolTypeUseCaseImpl implements GetHighSchoolTypeUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public GetHighSchoolTypeUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	public HighSchoolTypeResult getById(UUID id) {
		HighSchoolType highSchoolType = highSchoolTypeRepository.findById(id)
				.orElseThrow(() -> new HighSchoolTypeNotFoundException("High school type not found: " + id));
		return CreateHighSchoolTypeUseCaseImpl.toResult(highSchoolType);
	}
}
