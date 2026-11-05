package mx.edu.utez.sisa.admission.infrastructure.audit;

import mx.edu.utez.sisa.admission.domain.port.out.CandidateAuditPort;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Traduce la edición de la ficha al log de auditoría transversal. */
@Component
public class CandidateAuditAdapter implements CandidateAuditPort {

	static final String ACTION_FICHA_EDITED = "CANDIDATE_FICHA_EDITED";

	static final String ENTITY_CANDIDATE = "CANDIDATE";

	private final RecordAuditUseCase recordAuditUseCase;

	public CandidateAuditAdapter(RecordAuditUseCase recordAuditUseCase) {
		this.recordAuditUseCase = recordAuditUseCase;
	}

	@Override
	public void fichaEdited(UUID candidateId, UUID actorId, List<Change> changes) {
		if (changes == null || changes.isEmpty()) {
			return;
		}
		recordAuditUseCase.record(new RecordAuditUseCase.AuditRecord(actorId, ACTION_FICHA_EDITED, ENTITY_CANDIDATE,
				candidateId, changes.stream()
						.map(change -> new RecordAuditUseCase.FieldChange(change.field(), change.before(),
								change.after()))
						.toList()));
	}
}
