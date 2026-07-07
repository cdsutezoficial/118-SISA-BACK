package mx.edu.utez.sisa.academic_config.infrastructure.config;

import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PersonLookupPort;
import mx.edu.utez.sisa.academic_config.domain.service.ChangeAcademicDivisionStatusUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.CreateAcademicDivisionUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.ListAcademicDivisionsUseCaseImpl;
import mx.edu.utez.sisa.academic_config.domain.service.UpdateAcademicDivisionUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root wiring the 4 {@code academic_config} use case interactors
 * as Spring beans (design.md — Decision: Per-module composition root). The
 * {@code XxxUseCaseImpl} classes are plain, framework-agnostic classes (no
 * stereotype annotations, matching {@code identity}'s convention) so this is
 * the only place that constructs them with their out-port dependencies. A
 * sibling of {@code identity.UseCaseConfig}, not an extension of it — keeps
 * the module independently removable.
 */
@Configuration
public class UseCaseConfig {

	@Bean
	public CreateAcademicDivisionUseCase createAcademicDivisionUseCase(AcademicDivisionRepository divisionRepository,
			PersonLookupPort personLookupPort) {
		return new CreateAcademicDivisionUseCaseImpl(divisionRepository, personLookupPort);
	}

	@Bean
	public UpdateAcademicDivisionUseCase updateAcademicDivisionUseCase(AcademicDivisionRepository divisionRepository,
			PersonLookupPort personLookupPort) {
		return new UpdateAcademicDivisionUseCaseImpl(divisionRepository, personLookupPort);
	}

	@Bean
	public ListAcademicDivisionsUseCase listAcademicDivisionsUseCase(AcademicDivisionRepository divisionRepository) {
		return new ListAcademicDivisionsUseCaseImpl(divisionRepository);
	}

	@Bean
	public ChangeAcademicDivisionStatusUseCase changeAcademicDivisionStatusUseCase(
			AcademicDivisionRepository divisionRepository) {
		return new ChangeAcademicDivisionStatusUseCaseImpl(divisionRepository);
	}
}
