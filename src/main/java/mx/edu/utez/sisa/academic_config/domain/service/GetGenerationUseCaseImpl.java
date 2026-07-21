package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;

import java.util.UUID;

/**
 * Fetches a single {@code Generation} by id, 404 if missing — same pattern as
 * {@code GetAcademicPeriodUseCaseImpl}.
 */
public class GetGenerationUseCaseImpl implements GetGenerationUseCase {

	private final GenerationRepository generationRepository;

	public GetGenerationUseCaseImpl(GenerationRepository generationRepository) {
		this.generationRepository = generationRepository;
	}

	@Override
	public GenerationResult getById(UUID id) {
		Generation generation = generationRepository.findById(id)
				.orElseThrow(() -> new GenerationNotFoundException("Generation not found: " + id));
		return CreateGenerationUseCaseImpl.toResult(generation);
	}
}
