package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeSubjectClassificationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code SubjectClassification}'s status between {@code ACTIVE}
 * and {@code INACTIVE} (Phase 5 — ChangeStatus). Single interactor
 * parameterized by target status, mirroring
 * {@code ChangeAcademicDivisionStatusUseCaseImpl}.
 */
public class ChangeSubjectClassificationStatusUseCaseImpl implements ChangeSubjectClassificationStatusUseCase {

	private final SubjectClassificationRepository classificationRepository;

	public ChangeSubjectClassificationStatusUseCaseImpl(SubjectClassificationRepository classificationRepository) {
		this.classificationRepository = classificationRepository;
	}

	@Override
	@Transactional
	public ClassificationResult changeStatus(ChangeStatusCommand command) {
		SubjectClassification classification = classificationRepository.findById(command.classificationId())
				.orElseThrow(() -> new ClassificationNotFoundException(
						"Classification not found: " + command.classificationId()));

		if (command.target() == ClassificationStatus.ACTIVE) {
			classification.activate();
		}
		else {
			classification.deactivate();
		}
		SubjectClassification saved = classificationRepository.save(classification);

		return CreateSubjectClassificationUseCaseImpl.toResult(saved);
	}
}
