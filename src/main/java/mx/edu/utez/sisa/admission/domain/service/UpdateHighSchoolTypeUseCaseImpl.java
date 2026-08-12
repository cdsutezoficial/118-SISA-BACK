package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code HighSchoolType}'s {@code name}. No uniqueness to
 * revalidate — same convention as {@code UpdateOutreachChannelUseCaseImpl}.
 */
public class UpdateHighSchoolTypeUseCaseImpl implements UpdateHighSchoolTypeUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public UpdateHighSchoolTypeUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional
	public HighSchoolTypeResult updateHighSchoolType(UpdateHighSchoolTypeCommand command) {
		HighSchoolType highSchoolType = highSchoolTypeRepository.findById(command.highSchoolTypeId())
				.orElseThrow(() -> new HighSchoolTypeNotFoundException(
						"High school type not found: " + command.highSchoolTypeId()));

		highSchoolType.updateDetails(command.name());
		HighSchoolType saved = highSchoolTypeRepository.save(highSchoolType);

		return CreateHighSchoolTypeUseCaseImpl.toResult(saved);
	}
}
