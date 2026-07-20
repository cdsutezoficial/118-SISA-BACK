package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateClassificationCodeException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code SubjectClassification}'s catalog fields (spec:
 * "Update Subject Classification"). Revalidates {@code code} uniqueness,
 * excluding the record's own current row — a self-update with an unchanged
 * code must succeed. {@code name} is still deliberately NOT validated for
 * uniqueness, same rule as {@code CreateSubjectClassificationUseCaseImpl}.
 */
public class UpdateSubjectClassificationUseCaseImpl implements UpdateSubjectClassificationUseCase {

	private final SubjectClassificationRepository classificationRepository;

	public UpdateSubjectClassificationUseCaseImpl(SubjectClassificationRepository classificationRepository) {
		this.classificationRepository = classificationRepository;
	}

	@Override
	@Transactional
	public ClassificationResult updateClassification(UpdateClassificationCommand command) {
		SubjectClassification classification = classificationRepository.findById(command.classificationId())
				.orElseThrow(() -> new ClassificationNotFoundException(
						"Classification not found: " + command.classificationId()));

		classificationRepository.findByCode(command.code())
				.filter(found -> !found.getId().equals(classification.getId())).ifPresent(found -> {
					throw new DuplicateClassificationCodeException(
							"Classification code already in use: " + command.code());
				});

		classification.updateDetails(command.name(), command.code());
		SubjectClassification saved = classificationRepository.save(classification);

		return CreateSubjectClassificationUseCaseImpl.toResult(saved);
	}
}
