package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles an {@code AcademicProgram}'s status between {@code ACTIVE} and
 * {@code INACTIVE} (spec: "Activate and Deactivate Academic Program").
 * Single interactor parameterized by target status, mirroring
 * {@code ChangeAcademicDivisionStatusUseCaseImpl}.
 */
public class ChangeAcademicProgramStatusUseCaseImpl implements ChangeAcademicProgramStatusUseCase {

	private final AcademicProgramRepository programRepository;

	public ChangeAcademicProgramStatusUseCaseImpl(AcademicProgramRepository programRepository) {
		this.programRepository = programRepository;
	}

	@Override
	@Transactional
	public AcademicProgramResult changeStatus(ChangeStatusCommand command) {
		AcademicProgram program = programRepository.findById(command.programId())
				.orElseThrow(
						() -> new AcademicProgramNotFoundException("Academic program not found: " + command.programId()));

		if (command.target() == ProgramStatus.ACTIVE) {
			program.activate();
		} else {
			program.deactivate();
		}
		AcademicProgram saved = programRepository.save(program);

		return CreateAcademicProgramUseCaseImpl.toResult(saved);
	}
}
