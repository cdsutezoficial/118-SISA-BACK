package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code ProgramAdmissionConfig} by id, 404 if missing —
 * same pattern as {@code GetGenerationUseCaseImpl}.
 */
public class GetProgramAdmissionConfigUseCaseImpl implements GetProgramAdmissionConfigUseCase {

	private final ProgramAdmissionConfigRepository configRepository;

	public GetProgramAdmissionConfigUseCaseImpl(ProgramAdmissionConfigRepository configRepository) {
		this.configRepository = configRepository;
	}

	@Override
	public ProgramAdmissionConfigResult getById(UUID id) {
		ProgramAdmissionConfig config = configRepository.findById(id)
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException("Program admission config not found: " + id));
		return OpenProgramAdmissionUseCaseImpl.toResult(config);
	}
}
