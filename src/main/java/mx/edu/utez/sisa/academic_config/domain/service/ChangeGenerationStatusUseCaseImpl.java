package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Toggles a {@code Generation}'s status between {@code ACTIVE} and
 * {@code FINISHED} (simple 2-state toggle — see {@link GenerationStatus}).
 * Single interactor parameterized by target status, mirroring
 * {@code ChangeSubjectClassificationStatusUseCaseImpl}: unlike
 * {@code ChangeAcademicPeriodStatusUseCaseImpl}'s strict-sequence
 * enforcement, both directions are always valid here.
 */
public class ChangeGenerationStatusUseCaseImpl implements ChangeGenerationStatusUseCase {

	private final GenerationRepository generationRepository;

	public ChangeGenerationStatusUseCaseImpl(GenerationRepository generationRepository) {
		this.generationRepository = generationRepository;
	}

	@Override
	@Transactional
	public GenerationResult changeStatus(ChangeStatusCommand command) {
		Generation generation = generationRepository.findById(command.generationId()).orElseThrow(
				() -> new GenerationNotFoundException("Generation not found: " + command.generationId()));

		if (command.target() == GenerationStatus.ACTIVE) {
			generation.activate();
		}
		else {
			generation.finish();
		}
		Generation saved = generationRepository.save(generation);

		return CreateGenerationUseCaseImpl.toResult(saved);
	}
}
