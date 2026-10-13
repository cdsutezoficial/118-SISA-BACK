package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateOfferNameModalityException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramCodeException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an {@code AcademicProgram} catalog entry (spec: "Create Academic
 * Program"). Enforces {@code divisionId} existence via the existing
 * {@link AcademicDivisionRepository} (design.md — Decision: direct
 * same-module dependency, not a new port) and dual uniqueness on
 * {@code code} and {@code (offerName, modality)}.
 */
public class CreateAcademicProgramUseCaseImpl implements CreateAcademicProgramUseCase {

	private final AcademicProgramRepository programRepository;
	private final AcademicDivisionRepository divisionRepository;

	public CreateAcademicProgramUseCaseImpl(AcademicProgramRepository programRepository,
			AcademicDivisionRepository divisionRepository) {
		this.programRepository = programRepository;
		this.divisionRepository = divisionRepository;
	}

	@Override
	@Transactional
	public AcademicProgramResult createProgram(CreateAcademicProgramCommand command) {
		String name = AcademicProgramTextNormalizer.name(command.name());
		String offerName = AcademicProgramTextNormalizer.offerName(command.offerName());
		String code = AcademicProgramTextNormalizer.code(command.code());
		String description = AcademicProgramTextNormalizer.optional(command.description());
		String dgpCode = AcademicProgramTextNormalizer.optional(command.dgpCode());
		if (command.divisionId() == null || divisionRepository.findById(command.divisionId()).isEmpty()) {
			throw new DivisionNotFoundException("Academic division not found: " + command.divisionId());
		}
		if (programRepository.findByCode(code).isPresent()) {
			throw new DuplicateProgramCodeException("Program code already in use: " + code);
		}
		if (programRepository.findByOfferNameAndModality(offerName, command.modality()).isPresent()) {
			throw new DuplicateOfferNameModalityException("Program offerName+modality already in use: "
					+ offerName + " / " + command.modality());
		}

		AcademicProgram program = new AcademicProgram(command.divisionId(), name, offerName, code, command.level(),
				command.modality(), command.continuityProgramId(), description, dgpCode);
		AcademicProgram saved = programRepository.save(program);

		return toResult(saved);
	}

	static AcademicProgramResult toResult(AcademicProgram program) {
		return new AcademicProgramResult(program.getId(), program.getDivisionId(), program.getName(),
				program.getOfferName(), program.getCode(), program.getLevel(), program.getModality(),
				program.getContinuityProgramId(), program.getDescription(), program.getDgpCode(),
				program.getStatus());
	}
}
