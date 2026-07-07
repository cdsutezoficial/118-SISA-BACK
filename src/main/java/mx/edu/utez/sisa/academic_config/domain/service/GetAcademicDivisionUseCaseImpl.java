package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicDivision} by id (spec gap closed while
 * wiring the frontend view/edit screen — {@code ListAcademicDivisionsUseCase}
 * alone doesn't cleanly cover a single-item fetch).
 */
public class GetAcademicDivisionUseCaseImpl implements GetAcademicDivisionUseCase {

	private final AcademicDivisionRepository divisionRepository;

	public GetAcademicDivisionUseCaseImpl(AcademicDivisionRepository divisionRepository) {
		this.divisionRepository = divisionRepository;
	}

	@Override
	public AcademicDivisionResult getById(UUID id) {
		AcademicDivision division = divisionRepository.findById(id)
				.orElseThrow(() -> new AcademicDivisionNotFoundException("Academic division not found: " + id));
		return CreateAcademicDivisionUseCaseImpl.toResult(division);
	}
}
