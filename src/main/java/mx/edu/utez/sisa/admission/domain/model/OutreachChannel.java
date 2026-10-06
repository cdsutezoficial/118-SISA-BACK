package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
 * <p>The simplest aggregate in the entire project: no FKs, no child entities, a
 * single free-text {@code name} plus a plain ACTIVE/INACTIVE status.
 *
 * <p><b>{@code name} gained a uniqueness constraint in Fase 9 (2026-10-05).</b>
 * It shipped without one, deliberately, on the grounds that the domain doc did not
 * ask for uniqueness and a rule should not be invented — the same criterion
 * applied to {@code SubjectClassification} and {@code PaymentConcept}. The plan of
 * 2026-10-02 does ask for it, so the constraint is now here and this paragraph is
 * the record of the reversal. Note the contrast with those two siblings, which
 * still have no uniqueness on {@code name}: they carry a {@code code} whose
 * uniqueness is what their doc asks for, so constraining the display name too
 * would be inventing a second rule out of the same text.
 *
 * <p>See {@code docs/plans/2026-07-28-outreach-channel.md}.
 */
@Entity
@Table(name = "outreach_channel", uniqueConstraints = @UniqueConstraint(name = "uk_outreach_channel_name", columnNames = "name"))
public class OutreachChannel {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/**
	 * 150 is the same ceiling {@code AcademicDivision.name} uses for a catalog
	 * name, and comfortably above any real channel ("Referido familiar" is 17).
	 * It matches the {@code @Size} on the DTOs.
	 *
	 * <p><b>The constraint is case-sensitive, and that is not enough on its
	 * own.</b> The acceptance criterion of this phase is that {@code "Facebook"},
	 * {@code "facebook"} and {@code " Facebook "} are the same channel, and a
	 * plain {@code UNIQUE (name)} only stops the exact-duplicate half of that.
	 * The case-insensitive half rests on the Java check in
	 * {@code CreateOutreachChannelUseCaseImpl#requireUniqueName}, which queries
	 * with {@code IgnoreCase}. The column is not normalized to uppercase on
	 * purpose — the name is shown to the user in the list and in the reference
	 * pickers — so the two halves have to be split between the constraint and the
	 * query. An alternative would be a functional index on {@code LOWER(name)},
	 * which would make the database enforce the whole rule; not done because no
	 * other catalog in the project carries one and a single hand-written index
	 * would be the odd one out.
	 */
	@Column(nullable = false, length = 150)
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
