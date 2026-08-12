package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /program-admission-configs}: {@code {items[],
 * totalElements, totalPages, page, size}}.
 */
public record ProgramAdmissionConfigListResponse(List<ProgramAdmissionConfigListItemResponse> items,
		long totalElements, int totalPages, int page, int size) {
}
