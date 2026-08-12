package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code ProgramAdmissionConfig}'s {@code status} between
 * {@code OPEN} and {@code CLOSED} ONLY (simple 2-state toggle — see
 * {@link ProgramAdmissionConfigStatus}). Single interactor parameterized by
 * target status, mirroring {@code ChangeGenerationStatusUseCaseImpl}: both
 * directions are always valid. NEVER touches {@code selectionStatus} — see
 * {@link ProgramAdmissionConfig} class javadoc.
 */
public class ChangeProgramAdmissionConfigStatusUseCaseImpl implements ChangeProgramAdmissionConfigStatusUseCase {

	private final ProgramAdmissionConfigRepository configRepository;

	public ChangeProgramAdmissionConfigStatusUseCaseImpl(ProgramAdmissionConfigRepository configRepository) {
		this.configRepository = configRepository;
	}

	@Override
	@Transactional
	public ProgramAdmissionConfigResult changeStatus(ChangeStatusCommand command) {
		ProgramAdmissionConfig config = configRepository.findById(command.configId()).orElseThrow(
				() -> new ProgramAdmissionConfigNotFoundException(
						"Program admission config not found: " + command.configId()));

		if (command.target() == ProgramAdmissionConfigStatus.OPEN) {
			config.open();
		}
		else {
			config.close();
		}
		ProgramAdmissionConfig saved = configRepository.save(config);

		return OpenProgramAdmissionUseCaseImpl.toResult(saved);
	}
}
