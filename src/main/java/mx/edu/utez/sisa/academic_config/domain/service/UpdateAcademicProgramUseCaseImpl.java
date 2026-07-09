package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateOfferNameModalityException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramCodeException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code AcademicProgram}'s catalog fields (spec:
 * "Update Academic Program"). Applies the same {@code divisionId}-existence
 * and dual uniqueness rules as creation, except a program's own current
 * values never count as a conflict against itself.
 */
public class UpdateAcademicProgramUseCaseImpl implements UpdateAcademicProgramUseCase {

	private final AcademicProgramRepository programRepository;
	private final AcademicDivisionRepository divisionRepository;

	public UpdateAcademicProgramUseCaseImpl(AcademicProgramRepository programRepository,
			AcademicDivisionRepository divisionRepository) {
		this.programRepository = programRepository;
		this.divisionRepository = divisionRepository;
	}

	@Override
	@Transactional
	public AcademicProgramResult updateProgram(UpdateAcademicProgramCommand command) {
		AcademicProgram program = programRepository.findById(command.programId())
				.orElseThrow(
						() -> new AcademicProgramNotFoundException("Academic program not found: " + command.programId()));

		if (command.divisionId() == null || divisionRepository.findById(command.divisionId()).isEmpty()) {
			throw new DivisionNotFoundException("Academic division not found: " + command.divisionId());
		}
		programRepository.findByCode(command.code()).filter(found -> !found.getId().equals(program.getId()))
				.ifPresent(found -> {
					throw new DuplicateProgramCodeException("Program code already in use: " + command.code());
				});
		programRepository.findByOfferNameAndModality(command.offerName(), command.modality())
				.filter(found -> !found.getId().equals(program.getId())).ifPresent(found -> {
					throw new DuplicateOfferNameModalityException("Program offerName+modality already in use: "
							+ command.offerName() + " / " + command.modality());
				});

		program.updateDetails(command.divisionId(), command.name(), command.offerName(), command.code(),
				command.level(), command.modality(), command.continuityProgramId(), command.description(),
				command.dgpCode());
		AcademicProgram saved = programRepository.save(program);

		return CreateAcademicProgramUseCaseImpl.toResult(saved);
	}
}
