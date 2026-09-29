package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.port.out.ConfigurationStatisticsRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JPA-backed {@link ConfigurationStatisticsRepository} adapter delegating to
 * the aggregate JPA repositories' plain {@code count()} queries. This is a
 * read-side aggregation (mirrors {@code SubjectJpaRepository}'s rationale):
 * no domain out-port mutation paths are touched.
 */
@Component
public class ConfigurationStatisticsRepositoryAdapter implements ConfigurationStatisticsRepository {

	private final AcademicDivisionJpaRepository divisionJpaRepository;

	private final AcademicProgramJpaRepository programJpaRepository;

	private final SubjectJpaRepository subjectJpaRepository;

	private final GroupJpaRepository groupJpaRepository;

	public ConfigurationStatisticsRepositoryAdapter(AcademicDivisionJpaRepository divisionJpaRepository,
			AcademicProgramJpaRepository programJpaRepository, SubjectJpaRepository subjectJpaRepository,
			GroupJpaRepository groupJpaRepository) {
		this.divisionJpaRepository = divisionJpaRepository;
		this.programJpaRepository = programJpaRepository;
		this.subjectJpaRepository = subjectJpaRepository;
		this.groupJpaRepository = groupJpaRepository;
	}

	@Override
	public long countDivisions() {
		return divisionJpaRepository.count();
	}

	@Override
	public long countPrograms() {
		return programJpaRepository.count();
	}

	@Override
	public long countSubjects() {
		return subjectJpaRepository.count();
	}

	@Override
	public long countGroupsForPeriod(UUID periodId) {
		return groupJpaRepository.countByPeriodId(periodId);
	}
}