package mx.edu.utez.sisa.admission.infrastructure.audit;

import mx.edu.utez.sisa.admission.domain.port.out.CandidateAuditPort.Change;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase.AuditRecord;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CandidateAuditAdapterTest {

	private final RecordAuditUseCase recordAuditUseCase = mock(RecordAuditUseCase.class);

	private final CandidateAuditAdapter adapter = new CandidateAuditAdapter(recordAuditUseCase);

	@Test
	void recordsTheEditAsACandidateFichaEvent() {
		UUID candidate = UUID.randomUUID();
		UUID actor = UUID.randomUUID();

		adapter.fichaEdited(candidate, actor, List.of(new Change("curp", "A", "B")));

		verify(recordAuditUseCase).record(new AuditRecord(actor, "CANDIDATE_FICHA_EDITED", "CANDIDATE", candidate,
				List.of(new FieldChange("curp", "A", "B"))));
	}

	@Test
	void nothingChangedLeavesNoEntry() {
		adapter.fichaEdited(UUID.randomUUID(), UUID.randomUUID(), List.of());

		verifyNoInteractions(recordAuditUseCase);
	}
}
