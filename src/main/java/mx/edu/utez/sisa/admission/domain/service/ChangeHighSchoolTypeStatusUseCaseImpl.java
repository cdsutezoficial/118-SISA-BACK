package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code HighSchoolType}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeOutreachChannelStatusUseCaseImpl}.
 */
public class ChangeHighSchoolTypeStatusUseCaseImpl implements ChangeHighSchoolTypeStatusUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public ChangeHighSchoolTypeStatusUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional
	public HighSchoolTypeResult changeStatus(ChangeStatusCommand command) {
		HighSchoolType highSchoolType = highSchoolTypeRepository.findById(command.highSchoolTypeId())
				.orElseThrow(() -> new HighSchoolTypeNotFoundException(
						"High school type not found: " + command.highSchoolTypeId()));

		if (command.target() == HighSchoolTypeStatus.ACTIVE) {
			highSchoolType.activate();
		}
		else {
			highSchoolType.deactivate();
		}
		HighSchoolType saved = highSchoolTypeRepository.save(highSchoolType);

		return CreateHighSchoolTypeUseCaseImpl.toResult(saved);
	}
}
