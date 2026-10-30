package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;

import java.util.UUID;

/** Historial de cambios de carrera de los candidatos. */
public interface CandidateProgramChangeRepository {

	CandidateProgramChange save(CandidateProgramChange change);

	/** Cuántos cambios de carrera lleva el candidato (numera la referencia del cargo nuevo). */
	long countByCandidateId(UUID candidateId);
}
