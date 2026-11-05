package mx.edu.utez.sisa.audit.domain.port.in;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consulta del log de auditoría (RF-AUD-003): por rango de fechas, usuario que
 * hizo la acción, tipo de evento y entidad afectada. Solo ADMIN (RN-AUD-004).
 */
public interface QueryAuditLogUseCase {

	AuditPage search(AuditFilter filter, int page, int size);

	/** Todos los campos son opcionales; {@code to} es exclusivo. */
	record AuditFilter(Instant from, Instant to, UUID actorId, String action, String entityType, UUID entityId) {
	}

	record AuditPage(List<AuditEntry> items, long total, int page, int size) {
	}
}
