package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionNameException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an {@code AcademicDivision} catalog entry (spec: "Create Academic
 * Division"). Enforces name/code uniqueness and, when provided, that
 * {@code directorPersonId} references an existing {@code Person} via
 * {@link PersonLookupPort}.
 */
public class CreateAcademicDivisionUseCaseImpl implements CreateAcademicDivisionUseCase {

	private final AcademicDivisionRepository divisionRepository;
	private final PersonLookupPort personLookupPort;

	public CreateAcademicDivisionUseCaseImpl(AcademicDivisionRepository divisionRepository,
			PersonLookupPort personLookupPort) {
		this.divisionRepository = divisionRepository;
		this.personLookupPort = personLookupPort;
	}

	@Override
	@Transactional
	public AcademicDivisionResult createDivision(CreateAcademicDivisionCommand command) {
		if (divisionRepository.findByName(command.name()).isPresent()) {
			throw new DuplicateDivisionNameException("Division name already in use: " + command.name());
		}
		if (divisionRepository.findByCode(command.code()).isPresent()) {
			throw new DuplicateDivisionCodeException("Division code already in use: " + command.code());
		}
		if (command.directorPersonId() != null && !personLookupPort.existsById(command.directorPersonId())) {
			throw new DirectorNotFoundException("Director person not found: " + command.directorPersonId());
		}

		AcademicDivision division = new AcademicDivision(command.name(), command.code(), command.description(),
				command.directorPersonId());
		AcademicDivision saved = divisionRepository.save(division);

		return toResult(saved);
	}

	static AcademicDivisionResult toResult(AcademicDivision division) {
		return new AcademicDivisionResult(division.getId(), division.getName(), division.getCode(),
				division.getDescription(), division.getDirectorPersonId(), division.getStatus());
	}
}
