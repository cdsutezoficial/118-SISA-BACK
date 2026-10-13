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
			String name = AcademicProgramTextNormalizer.name(command.name());
			String offerName = AcademicProgramTextNormalizer.offerName(command.offerName());
			String code = AcademicProgramTextNormalizer.code(command.code());
			String description = AcademicProgramTextNormalizer.optional(command.description());
			String dgpCode = AcademicProgramTextNormalizer.optional(command.dgpCode());

		if (command.divisionId() == null || divisionRepository.findById(command.divisionId()).isEmpty()) {
			throw new DivisionNotFoundException("Academic division not found: " + command.divisionId());
		}
		programRepository.findByCode(code).filter(found -> !found.getId().equals(program.getId()))
				.ifPresent(found -> {
					throw new DuplicateProgramCodeException("Program code already in use: " + code);
				});
		programRepository.findByOfferNameAndModality(offerName, command.modality())
				.filter(found -> !found.getId().equals(program.getId())).ifPresent(found -> {
					throw new DuplicateOfferNameModalityException("Program offerName+modality already in use: "
							+ offerName + " / " + command.modality());
				});

		program.updateDetails(command.divisionId(), name, offerName, code,
				command.level(), command.modality(), command.continuityProgramId(), description, dgpCode);
		AcademicProgram saved = programRepository.save(program);

		return CreateAcademicProgramUseCaseImpl.toResult(saved);
	}
}
