package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link ProgramAdmissionConfigQueryPort} adapter: config lookup
 * via {@link ProgramAdmissionConfigLookupJpaRepository}, program name via
 * {@link AcademicProgramNameLookupJpaRepository} (two reads — the entities
 * live in {@code academic_config} and share no JPA association, so a single
 * JPQL join is not available). Maps only the minimal projection the admission
 * flow needs ({@code id}, {@code status}, {@code programName}); the full
 * {@code ProgramAdmissionConfig} shape stays inside {@code academic_config}.
 */
@Component
public class ProgramAdmissionConfigQueryAdapter implements ProgramAdmissionConfigQueryPort {

	private final ProgramAdmissionConfigLookupJpaRepository configJpaRepository;

	private final AcademicProgramNameLookupJpaRepository programJpaRepository;

	public ProgramAdmissionConfigQueryAdapter(
			ProgramAdmissionConfigLookupJpaRepository configJpaRepository,
			AcademicProgramNameLookupJpaRepository programJpaRepository) {
		this.configJpaRepository = configJpaRepository;
		this.programJpaRepository = programJpaRepository;
	}

	@Override
	public Optional<AdmissionConfigInfo> findById(UUID id) {
		return configJpaRepository.findById(id).map(config -> {
			String programName = programJpaRepository.findById(config.getProgramId())
					.map(program -> program.getName())
					.orElse(null);
			return new AdmissionConfigInfo(config.getId(), config.getStatus(), programName);
		});
	}
}