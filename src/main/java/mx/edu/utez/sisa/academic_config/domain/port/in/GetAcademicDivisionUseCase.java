package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicDivision} by id (frontend gap closed after
 * the {@code academic-config-divisions} change was archived: the view/edit
 * screen needs a single-item fetch that {@code ListAcademicDivisionsUseCase}
 * doesn't cleanly cover). Reuses {@link AcademicDivisionResult} — same shape
 * as Create/Update/ChangeStatus.
 */
public interface GetAcademicDivisionUseCase {

	AcademicDivisionResult getById(UUID id);
}
