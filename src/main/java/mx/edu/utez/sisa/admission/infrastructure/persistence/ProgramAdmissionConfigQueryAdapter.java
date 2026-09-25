package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link ProgramAdmissionConfigQueryPort} adapter: config lookup
 * via {@link ProgramAdmissionConfigLookupJpaRepository}, program name/modality
 * via {@link AcademicProgramNameLookupJpaRepository} and destination-period
 * name via {@link AcademicPeriodNameLookupJpaRepository} (the entities live in
 * {@code academic_config} and share no JPA association, so a single JPQL join
 * is not available). Maps only the minimal projection the admission flow needs
 * ({@code id}, {@code status}, {@code programName}, {@code modality},
 * {@code periodName}); the full {@code ProgramAdmissionConfig} shape stays
 * inside {@code academic_config}.
 */
@Component
public class ProgramAdmissionConfigQueryAdapter implements ProgramAdmissionConfigQueryPort {

	private final ProgramAdmissionConfigLookupJpaRepository configJpaRepository;

	private final AcademicProgramNameLookupJpaRepository programJpaRepository;

	private final AcademicPeriodNameLookupJpaRepository periodJpaRepository;

	public ProgramAdmissionConfigQueryAdapter(
			ProgramAdmissionConfigLookupJpaRepository configJpaRepository,
			AcademicProgramNameLookupJpaRepository programJpaRepository,
			AcademicPeriodNameLookupJpaRepository periodJpaRepository) {
		this.configJpaRepository = configJpaRepository;
		this.programJpaRepository = programJpaRepository;
		this.periodJpaRepository = periodJpaRepository;
	}

	@Override
	public Optional<AdmissionConfigInfo> findById(UUID id) {
		return configJpaRepository.findById(id).map(config -> {
			String programName = null;
			ProgramModality modality = null;
			if (config.getProgramId() != null) {
				var program = programJpaRepository.findById(config.getProgramId());
				programName = program.map(p -> p.getName()).orElse(null);
				modality = program.map(p -> p.getModality()).orElse(null);
			}
			String periodName = config.getPeriodId() == null ? null
					: periodJpaRepository.findById(config.getPeriodId()).map(p -> p.getName()).orElse(null);
			return new AdmissionConfigInfo(config.getId(), config.getStatus(), config.getProgramId(), programName,
					modality, periodName);
		});
	}
}