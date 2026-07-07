package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionNameException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code AcademicDivision}'s catalog fields (spec:
 * "Update Academic Division"). Applies the same uniqueness and
 * director-existence rules as creation, except a division's own current
 * {@code name}/{@code code} never counts as a conflict against itself.
 */
public class UpdateAcademicDivisionUseCaseImpl implements UpdateAcademicDivisionUseCase {

	private final AcademicDivisionRepository divisionRepository;
	private final PersonLookupPort personLookupPort;

	public UpdateAcademicDivisionUseCaseImpl(AcademicDivisionRepository divisionRepository,
			PersonLookupPort personLookupPort) {
		this.divisionRepository = divisionRepository;
		this.personLookupPort = personLookupPort;
	}

	@Override
	@Transactional
	public AcademicDivisionResult updateDivision(UpdateAcademicDivisionCommand command) {
		AcademicDivision division = divisionRepository.findById(command.divisionId())
				.orElseThrow(() -> new AcademicDivisionNotFoundException(
						"Academic division not found: " + command.divisionId()));

		divisionRepository.findByName(command.name())
				.filter(found -> !found.getId().equals(division.getId())).ifPresent(found -> {
					throw new DuplicateDivisionNameException("Division name already in use: " + command.name());
				});
		divisionRepository.findByCode(command.code())
				.filter(found -> !found.getId().equals(division.getId())).ifPresent(found -> {
					throw new DuplicateDivisionCodeException("Division code already in use: " + command.code());
				});
		if (command.directorPersonId() != null && !personLookupPort.existsById(command.directorPersonId())) {
			throw new DirectorNotFoundException("Director person not found: " + command.directorPersonId());
		}

		division.updateDetails(command.name(), command.code(), command.description(), command.directorPersonId());
		AcademicDivision saved = divisionRepository.save(division);

		return CreateAcademicDivisionUseCaseImpl.toResult(saved);
	}
}
