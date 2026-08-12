package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;

import java.time.Instant;
import java.util.UUID;

/**
 * Updates an existing {@code ProgramAdmissionConfig}'s catalog fields (PUT
 * {@code /program-admission-configs/{id}}). {@code (programId, periodId)}
 * uniqueness is revalidated, EXCLUDING the record's own current row — same
 * self-update rule as {@code UpdateGenerationUseCase}'s
 * {@code (programId, number)} revalidation. {@code status} is deliberately
 * absent — status transitions are the sole responsibility of
 * {@code ChangeProgramAdmissionConfigStatusUseCase}. {@code selectionStatus}
 * is ALSO absent and never touched by this use case (plan §3/§9 — out of
 * scope in this phase).
 */
public interface UpdateProgramAdmissionConfigUseCase {

	ProgramAdmissionConfigResult updateProgramAdmissionConfig(UpdateProgramAdmissionConfigCommand command);

	record UpdateProgramAdmissionConfigCommand(UUID configId, UUID programId, UUID periodId, UUID targetGenerationId,
			boolean isOffered, int maxCandidates, Instant opensAt, Instant closesAt) {
	}
}
