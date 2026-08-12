package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code HighSchoolType} catalog entry. No uniqueness check on
 * {@code name} — deliberately, per the domain doc (see
 * {@code HighSchoolType}'s javadoc), same as
 * {@code CreateOutreachChannelUseCaseImpl}.
 */
public class CreateHighSchoolTypeUseCaseImpl implements CreateHighSchoolTypeUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public CreateHighSchoolTypeUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional
	public HighSchoolTypeResult createHighSchoolType(CreateHighSchoolTypeCommand command) {
		HighSchoolType highSchoolType = new HighSchoolType(command.name());
		HighSchoolType saved = highSchoolTypeRepository.save(highSchoolType);

		return toResult(saved);
	}

	static HighSchoolTypeResult toResult(HighSchoolType highSchoolType) {
		return new HighSchoolTypeResult(highSchoolType.getId(), highSchoolType.getName(), highSchoolType.getStatus());
	}
}
