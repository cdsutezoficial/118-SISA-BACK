package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicProgram} by id (spec: "Get Academic
 * Program by Id").
 */
public class GetAcademicProgramUseCaseImpl implements GetAcademicProgramUseCase {

	private final AcademicProgramRepository programRepository;

	public GetAcademicProgramUseCaseImpl(AcademicProgramRepository programRepository) {
		this.programRepository = programRepository;
	}

	@Override
	public AcademicProgramResult getById(UUID id) {
		AcademicProgram program = programRepository.findById(id)
				.orElseThrow(() -> new AcademicProgramNotFoundException("Academic program not found: " + id));
		return CreateAcademicProgramUseCaseImpl.toResult(program);
	}
}
