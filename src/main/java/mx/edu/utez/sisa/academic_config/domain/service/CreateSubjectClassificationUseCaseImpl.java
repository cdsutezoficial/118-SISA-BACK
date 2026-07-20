package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateClassificationCodeException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code SubjectClassification} catalog entry (spec: "Create
 * Subject Classification"). Enforces {@code code} uniqueness only —
 * {@code name} is deliberately NOT validated for uniqueness (unlike
 * {@code CreateAcademicDivisionUseCaseImpl}), per the domain doc
 * ({@code 02-config-academica.md} lines 15-24).
 */
public class CreateSubjectClassificationUseCaseImpl implements CreateSubjectClassificationUseCase {

	private final SubjectClassificationRepository classificationRepository;

	public CreateSubjectClassificationUseCaseImpl(SubjectClassificationRepository classificationRepository) {
		this.classificationRepository = classificationRepository;
	}

	@Override
	@Transactional
	public ClassificationResult createClassification(CreateClassificationCommand command) {
		if (classificationRepository.findByCode(command.code()).isPresent()) {
			throw new DuplicateClassificationCodeException("Classification code already in use: " + command.code());
		}

		SubjectClassification classification = new SubjectClassification(command.name(), command.code());
		SubjectClassification saved = classificationRepository.save(classification);

		return toResult(saved);
	}

	static ClassificationResult toResult(SubjectClassification classification) {
		return new ClassificationResult(classification.getId(), classification.getName(), classification.getCode(),
				classification.getStatus());
	}
}
