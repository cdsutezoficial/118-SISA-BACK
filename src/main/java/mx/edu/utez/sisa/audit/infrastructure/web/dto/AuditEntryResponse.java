package mx.edu.utez.sisa.audit.infrastructure.web.dto;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(UUID id, Instant occurredAt, UUID actorId, String actorName, String action,
		String entityType, UUID entityId, String field, String oldValue, String newValue) {

	public static AuditEntryResponse from(AuditEntry entry) {
		return new AuditEntryResponse(entry.getId(), entry.getOccurredAt(), entry.getActorId(), entry.getActorName(),
				entry.getAction(), entry.getEntityType(), entry.getEntityId(), entry.getField(), entry.getOldValue(),
				entry.getNewValue());
	}
}
