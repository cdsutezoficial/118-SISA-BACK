package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles an {@code AcademicDivision}'s status between {@code ACTIVE} and
 * {@code INACTIVE} (spec: "Activate and Deactivate Academic Division").
 * Single interactor parameterized by target status (design.md — Decision:
 * Single status-change use case + idempotent PATCH).
 */
public class ChangeAcademicDivisionStatusUseCaseImpl implements ChangeAcademicDivisionStatusUseCase {

	private final AcademicDivisionRepository divisionRepository;

	public ChangeAcademicDivisionStatusUseCaseImpl(AcademicDivisionRepository divisionRepository) {
		this.divisionRepository = divisionRepository;
	}

	@Override
	@Transactional
	public AcademicDivisionResult changeStatus(ChangeStatusCommand command) {
		AcademicDivision division = divisionRepository.findById(command.divisionId())
				.orElseThrow(() -> new AcademicDivisionNotFoundException(
						"Academic division not found: " + command.divisionId()));

		if (command.target() == DivisionStatus.ACTIVE) {
			division.activate();
		} else {
			division.deactivate();
		}
		AcademicDivision saved = divisionRepository.save(division);

		return CreateAcademicDivisionUseCaseImpl.toResult(saved);
	}
}
