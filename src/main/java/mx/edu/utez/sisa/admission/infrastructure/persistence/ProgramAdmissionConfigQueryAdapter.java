package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicDivisionJpaRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

	private final AcademicDivisionJpaRepository divisionJpaRepository;

	private final AcademicPeriodNameLookupJpaRepository periodJpaRepository;

	public ProgramAdmissionConfigQueryAdapter(
			ProgramAdmissionConfigLookupJpaRepository configJpaRepository,
			AcademicProgramNameLookupJpaRepository programJpaRepository,
			AcademicDivisionJpaRepository divisionJpaRepository,
			AcademicPeriodNameLookupJpaRepository periodJpaRepository) {
		this.configJpaRepository = configJpaRepository;
		this.programJpaRepository = programJpaRepository;
		this.divisionJpaRepository = divisionJpaRepository;
		this.periodJpaRepository = periodJpaRepository;
	}

	@Override
	public Optional<AdmissionConfigInfo> findById(UUID id) {
		return configJpaRepository.findById(id).map(config -> {
			String programName = null;
			String divisionName = null;
			ProgramModality modality = null;
			if (config.getProgramId() != null) {
				Optional<AcademicProgram> program = programJpaRepository.findById(config.getProgramId());
				programName = program.map(p -> p.getName()).orElse(null);
				divisionName = program.map(AcademicProgram::getDivisionId)
						.flatMap(divisionJpaRepository::findById)
						.map(AcademicDivision::getName)
						.orElse(null);
				modality = program.map(p -> p.getModality()).orElse(null);
			}
			String periodName = config.getPeriodId() == null ? null
					: periodJpaRepository.findById(config.getPeriodId()).map(p -> p.getName()).orElse(null);
			return new AdmissionConfigInfo(config.getId(), config.getStatus(), config.getProgramId(), programName,
					divisionName, modality, periodName, config.getOpensAt(), config.getClosesAt(),
					config.getMaxCandidates());
		});
	}

	@Override
	public Map<UUID, ProgramRef> findProgramRefsByConfigIds(List<UUID> configIds) {
		if (configIds == null || configIds.isEmpty()) {
			return Map.of();
		}
		// Distinct because a page of candidates can repeat the same config.
		List<ProgramAdmissionConfig> configs = configJpaRepository.findAllById(configIds.stream().distinct().toList());
		Map<UUID, UUID> programIdByConfigId = configs.stream().filter(config -> config.getProgramId() != null)
				.collect(Collectors.toMap(ProgramAdmissionConfig::getId, ProgramAdmissionConfig::getProgramId));
		if (programIdByConfigId.isEmpty()) {
			return Map.of();
		}
		Set<UUID> programIds = new HashSet<>(programIdByConfigId.values());
		// A missing program row leaves a null name but keeps the program id: the
		// filter still works off the id, and the row renders with no name rather
		// than disappearing.
		Map<UUID, String> nameByProgramId = programJpaRepository.findAllById(programIds).stream()
				.collect(Collectors.toMap(AcademicProgram::getId, AcademicProgram::getName, (first, ignored) -> first));
		return programIdByConfigId.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
				entry -> new ProgramRef(entry.getValue(), nameByProgramId.get(entry.getValue()))));
	}
}