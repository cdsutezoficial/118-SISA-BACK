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
 *
 * <p>{@code name} and {@code code} are normalized by
 * {@link SubjectClassificationTextNormalizer} <b>before</b> the duplicate check
 * and before persisting, so the comparison always sees the same form the column
 * will hold. Without it, {@code " INT-C-ADM "} would evade the
 * {@code findByCodeIgnoreCase} check — which is case-insensitive but not
 * whitespace-insensitive — and end up stored next to {@code "INT-C-ADM"}.
 */
public class CreateSubjectClassificationUseCaseImpl implements CreateSubjectClassificationUseCase {

	private final SubjectClassificationRepository classificationRepository;

	public CreateSubjectClassificationUseCaseImpl(SubjectClassificationRepository classificationRepository) {
		this.classificationRepository = classificationRepository;
	}

	@Override
	@Transactional
	public ClassificationResult createClassification(CreateClassificationCommand command) {
		String name = SubjectClassificationTextNormalizer.name(command.name());
		String code = SubjectClassificationTextNormalizer.code(command.code());

		if (classificationRepository.findByCode(code).isPresent()) {
			throw new DuplicateClassificationCodeException("Classification code already in use: " + code);
		}

		SubjectClassification classification = new SubjectClassification(name, code);
		SubjectClassification saved = classificationRepository.save(classification);

		return toResult(saved);
	}

	static ClassificationResult toResult(SubjectClassification classification) {
		return new ClassificationResult(classification.getId(), classification.getName(), classification.getCode(),
				classification.getStatus());
	}
}
