package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AdmissionDashboardQueryAdapter implements AdmissionDashboardQueryPort {

	private final AdmissionDashboardJpaRepository repository;

	public AdmissionDashboardQueryAdapter(AdmissionDashboardJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<PeriodRef> findPeriod(UUID periodId) {
		return repository.findPeriod(periodId).stream().findFirst().map(AdmissionDashboardQueryAdapter::period);
	}

	@Override
	public Optional<PeriodRef> findLatestAdmissionPeriod() {
		return repository.findLatestAdmissionPeriods(PageRequest.of(0, 1)).stream().findFirst()
				.map(AdmissionDashboardQueryAdapter::period);
	}

	@Override
	public List<StatusCount> countByProgramAndStatus(UUID periodId, UUID divisionId) {
		return repository.countByProgramAndStatus(periodId, divisionId).stream()
				.map(row -> new StatusCount((UUID) row[0], (String) row[1], (ProgramModality) row[2],
						(CandidateStatus) row[3], Boolean.TRUE.equals(row[4]), ((Number) row[5]).longValue()))
				.toList();
	}

	private static PeriodRef period(Object[] row) {
		return new PeriodRef((UUID) row[0], (String) row[1]);
	}
}
