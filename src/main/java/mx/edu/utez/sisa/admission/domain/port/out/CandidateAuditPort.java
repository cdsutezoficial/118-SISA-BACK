package mx.edu.utez.sisa.admission.domain.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Deja en el log de auditoría transversal (RF-AUD-001) lo que cambió al editar
 * la ficha de un candidato (F4): quién, cuándo, campo, antes y después.
 */
public interface CandidateAuditPort {

	void fichaEdited(UUID candidateId, UUID actorId, List<Change> changes);

	record Change(String field, String before, String after) {
	}
}
