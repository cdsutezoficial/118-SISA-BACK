package mx.edu.utez.sisa.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Una entrada del log de auditoría transversal (RF-AUD-001/002): quién, cuándo,
 * qué acción, sobre qué entidad y, cuando aplica, el campo con su valor anterior
 * y el nuevo. Un cambio de varios campos deja una entrada por campo, todas con
 * la misma {@code occurredAt}.
 *
 * <p>Solo de lectura una vez escrita (RN-AUD-001): {@link Immutable}, columnas no
 * actualizables y sin setters; no hay caso de uso que la edite ni la borre.
 *
 * <p>{@code actorName} es la foto del nombre al momento del cambio, para que el
 * log siga legible aunque el usuario cambie de nombre o se dé de baja.
 */
@Entity
@Immutable
@Table(name = "audit_entry", indexes = {
		@Index(name = "ix_audit_entity", columnList = "entity_type, entity_id, occurred_at"),
		@Index(name = "ix_audit_actor", columnList = "actor_id, occurred_at"),
		@Index(name = "ix_audit_occurred", columnList = "occurred_at") })
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Column(name = "actor_name", updatable = false)
	private String actorName;

	@Column(nullable = false, length = 80, updatable = false)
	private String action;

	@Column(name = "entity_type", nullable = false, length = 60, updatable = false)
	private String entityType;

	@Column(name = "entity_id", updatable = false)
	private UUID entityId;

	@Column(length = 120, updatable = false)
	private String field;

	@Column(name = "old_value", length = 2000, updatable = false)
	private String oldValue;

	@Column(name = "new_value", length = 2000, updatable = false)
	private String newValue;

	protected AuditEntry() {
	}

	public AuditEntry(Instant occurredAt, UUID actorId, String actorName, String action, String entityType,
			UUID entityId, String field, String oldValue, String newValue) {
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.actorName = actorName;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.field = field;
		this.oldValue = oldValue;
		this.newValue = newValue;
	}

	public UUID getId() {
		return id;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public UUID getActorId() {
		return actorId;
	}

	public String getActorName() {
		return actorName;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public UUID getEntityId() {
		return entityId;
	}

	public String getField() {
		return field;
	}

	public String getOldValue() {
		return oldValue;
	}

	public String getNewValue() {
		return newValue;
	}
}
