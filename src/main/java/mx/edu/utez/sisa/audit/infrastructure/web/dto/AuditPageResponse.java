package mx.edu.utez.sisa.audit.infrastructure.web.dto;

import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditPage;

import java.util.List;

public record AuditPageResponse(List<AuditEntryResponse> items, long total, int page, int size) {

	public static AuditPageResponse from(AuditPage page) {
		return new AuditPageResponse(page.items().stream().map(AuditEntryResponse::from).toList(), page.total(),
				page.page(), page.size());
	}
}
