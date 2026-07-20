package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.SubjectClassificationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ClassificationNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code SubjectClassification} by id, 404 if missing — same
 * pattern as {@code GetAcademicDivisionUseCaseImpl}.
 */
public class GetSubjectClassificationUseCaseImpl implements GetSubjectClassificationUseCase {

	private final SubjectClassificationRepository classificationRepository;

	public GetSubjectClassificationUseCaseImpl(SubjectClassificationRepository classificationRepository) {
		this.classificationRepository = classificationRepository;
	}

	@Override
	public ClassificationResult getById(UUID id) {
		SubjectClassification classification = classificationRepository.findById(id)
				.orElseThrow(() -> new ClassificationNotFoundException("Classification not found: " + id));
		return CreateSubjectClassificationUseCaseImpl.toResult(classification);
	}
}
