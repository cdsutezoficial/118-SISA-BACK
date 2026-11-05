package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecturas agregadas para el tablero de Admisión: no carga candidatos, solo cuenta. */
public interface AdmissionDashboardQueryPort {

	Optional<PeriodRef> findPeriod(UUID periodId);

	/**
	 * El periodo del proceso de admisión más reciente: el de una configuración
	 * abierta, o si no hay, el de la última que abrió.
	 */
	Optional<PeriodRef> findLatestAdmissionPeriod();

	/**
	 * Cuántas fichas hay por carrera, estado y si están habilitadas para inducción,
	 * en las configuraciones de ese periodo.
	 *
	 * @param divisionId solo las carreras de esa división; {@code null} para todas
	 */
	List<StatusCount> countByProgramAndStatus(UUID periodId, UUID divisionId);

	record PeriodRef(UUID id, String name) {
	}

	record StatusCount(UUID programId, String programName, ProgramModality modality, CandidateStatus status,
			boolean inductionEnabled, long count) {
	}
}
