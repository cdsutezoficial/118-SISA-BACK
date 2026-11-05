package mx.edu.utez.sisa.audit.domain.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Registra un movimiento en el log de auditoría (RF-AUD-001). Corre dentro de la
 * transacción de quien lo llama: el cambio y su rastro se confirman o se
 * revierten juntos, así no queda un cambio sin auditar ni una auditoría de un
 * cambio que no ocurrió.
 */
public interface RecordAuditUseCase {

	void record(AuditRecord record);

	/**
	 * @param actorId    quién hizo el cambio; {@code null} si fue el sistema
	 * @param action     qué se hizo, p. ej. {@code CANDIDATE_FICHA_EDITED}
	 * @param entityType sobre qué tipo de entidad, p. ej. {@code CANDIDATE}
	 * @param entityId   cuál
	 * @param changes    los campos que cambiaron; vacío deja una sola entrada sin campo
	 */
	record AuditRecord(UUID actorId, String action, String entityType, UUID entityId, List<FieldChange> changes) {

		public AuditRecord {
			changes = changes == null ? List.of() : List.copyOf(changes);
		}
	}

	record FieldChange(String field, String before, String after) {
	}
}
