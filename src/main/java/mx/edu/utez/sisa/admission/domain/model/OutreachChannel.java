package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Outreach channel catalog aggregate root — first real piece of the
 * {@code admission} bounded context (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}, section
 * "Catálogos", plus {@code docs/requirements/02-ADMISION.md} RF-ADM-011,
 * "Selección de carrera": "Medio de difusión por el que se enteró").
 * Represents the channel a candidate learned about the university through
 * (Facebook, family referral, education fair, etc.) — configurable without
 * touching code. {@code Candidate} will reference this catalog via
 * {@code outreachChannelId} once that aggregate is built (out of scope here).
 *
 * <p>The simplest aggregate in the entire project: no FKs, no child entities,
 * a single free-text {@code name} with NO uniqueness constraint (same
 * criterion already applied to {@code SubjectClassification}/
 * {@code PaymentConcept} — the domain doc does not ask for uniqueness on this
 * catalog, so none is invented here) plus a plain ACTIVE/INACTIVE status.
 *
 * <p>See {@code docs/plans/2026-07-28-outreach-channel.md}.
 */
@Entity
@Table(name = "outreach_channel")
public class OutreachChannel {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OutreachChannelStatus status;

	protected OutreachChannel() {
		// JPA
	}

	public OutreachChannel(String name) {
		this.name = name;
		this.status = OutreachChannelStatus.ACTIVE;
	}

	/**
	 * Updates the catalog's {@code name}. {@code status} is deliberately
	 * absent — status transitions are the sole responsibility of
	 * {@code ChangeOutreachChannelStatusUseCase}, same separation as
	 * {@code SubjectClassification#updateDetails}.
	 */
	public void updateDetails(String name) {
		this.name = name;
	}

	/**
	 * Transitions to {@code ACTIVE}. Idempotent — calling on an
	 * already-{@code ACTIVE} channel is a no-op, same convention as
	 * {@code SubjectClassification#activate}.
	 */
	public void activate() {
		this.status = OutreachChannelStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE}. Idempotent — calling on an
	 * already-{@code INACTIVE} channel is a no-op. The record itself is never
	 * deleted, same convention as {@code SubjectClassification#deactivate}.
	 */
	public void deactivate() {
		this.status = OutreachChannelStatus.INACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public OutreachChannelStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OutreachChannel that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
