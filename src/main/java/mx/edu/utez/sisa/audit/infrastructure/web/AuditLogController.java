package mx.edu.utez.sisa.audit.infrastructure.web;

import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditFilter;
import mx.edu.utez.sisa.audit.infrastructure.web.dto.AuditPageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Consulta del log de auditoría (RF-AUD-003). Solo ADMIN (RN-AUD-004); no hay
 * rutas para editar ni borrar entradas (RN-AUD-001).
 */
@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

	private final QueryAuditLogUseCase queryAuditLogUseCase;

	public AuditLogController(QueryAuditLogUseCase queryAuditLogUseCase) {
		this.queryAuditLogUseCase = queryAuditLogUseCase;
	}

	@GetMapping
	public ResponseEntity<AuditPageResponse> search(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
			@RequestParam(required = false) String entityType, @RequestParam(required = false) UUID entityId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return ResponseEntity.ok(AuditPageResponse.from(queryAuditLogUseCase
				.search(new AuditFilter(from, to, actorId, action, entityType, entityId), page, size)));
	}
}
