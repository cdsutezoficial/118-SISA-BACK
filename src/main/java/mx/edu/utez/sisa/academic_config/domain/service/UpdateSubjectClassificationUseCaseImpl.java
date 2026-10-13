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
 *
 * <p>{@code name} and {@code code} are normalized by
 * {@link SubjectClassificationTextNormalizer} <b>before</b> the duplicate check
 * and before persisting. The self-exclusion filter stays as it is, but it now
 * compares normalized codes: re-saving a record with its own code typed as
 * {@code " int-c-adm "} resolves to the same {@code INT-C-ADM} the row already
 * holds, so the update succeeds instead of reporting a conflict with itself.
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

		String name = SubjectClassificationTextNormalizer.name(command.name());
		String code = SubjectClassificationTextNormalizer.code(command.code());

		classificationRepository.findByCode(code)
				.filter(found -> !found.getId().equals(classification.getId())).ifPresent(found -> {
					throw new DuplicateClassificationCodeException(
							"Classification code already in use: " + code);
				});

		classification.updateDetails(name, code);
		SubjectClassification saved = classificationRepository.save(classification);

		return CreateSubjectClassificationUseCaseImpl.toResult(saved);
	}
}
