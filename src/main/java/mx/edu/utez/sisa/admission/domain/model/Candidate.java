package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Candidate aggregate root — the applicant's admission ticket ("ficha de
 * admisión"), per {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}.
 * Created by the public, unauthenticated {@code RegisterCandidateUseCase}
 * (project stage: "la ficha que llena el estudiante"); {@code personId} and
 * the rich Person profile (address, health, diversity, employment, school
 * background) land in the shared {@code Person} aggregate in the same
 * transaction.
 *
 * <p>{@code personId}, {@code admissionConfigId}, {@code selectedBy} and
 * {@code outreachChannelId} are plain {@code UUID} columns with NO JPA
 * relationship — Person and OutreachChannel are cross-aggregate /
 * cross-bounded-context references, and User is in another bounded context
 * entirely, which this codebase always models as bare FK columns (same
 * convention as {@code Group#generationId} / {@code ProgramAdmissionConfig}).
 */
@Entity
@Table(name = "candidate")
public class Candidate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "person_id", nullable = false)
	private UUID personId;

	/** Reference to the {@code ProgramAdmissionConfig} the applicant chose (defines program, destination period, generation). */
	@Column(name = "admission_config_id", nullable = false)
	private UUID admissionConfigId;

	/**
	 * The applicant's ficha number, and the key she comes back with
	 * ("vuelve a pagar mi ficha" pairs it with the last 3 CURP characters), so a
	 * duplicate here is worse than a wasted row: two applicants would be looking
	 * up the same ficha.
	 *
	 * <p>{@code generateFolio()} derives it as {@code count(prefix) + 1}, which is
	 * read-then-write with no lock — two concurrent registrations can compute the
	 * same number. This index is what makes that collision visible as a
	 * {@code DuplicateKeyException} instead of silent corruption. See the migration
	 * script for applying it outside {@code ddl-auto=update} environments.
	 */
	@Column(nullable = false, unique = true)
	private String folio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CandidateStatus status;

	@Column(name = "llave_mx_verified", nullable = false)
	private boolean llaveMxVerified;

	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@Column(name = "selected_by")
	private UUID selectedBy;

	@Column(name = "selected_at")
	private Instant selectedAt;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "is_first_choice", nullable = false)
	private boolean isFirstChoice;

	@Column(name = "outreach_channel_id")
	private UUID outreachChannelId;

	@Column(name = "is_enabled_for_induction", nullable = false)
	private boolean isEnabledForInduction;

	/**
	 * The ficha's charge in finance ({@code payment.id}). Since 2026-10-23 the
	 * ficha has no table of its own: its reference, deadline, status, receipt
	 * and price all live on that charge, and this is the only link to it.
	 */
	@Column(name = "ficha_payment_id")
	private UUID fichaPaymentId;

	/**
	 * El cargo que dejó un cambio de carrera con la ficha ya pagada y que todavía
	 * falta pagar (la diferencia). Mientras exista, {@link #fichaPaymentId} sigue
	 * apuntando al cargo pagado, que es el que le guarda su lugar en el cupo; al
	 * pagarse, este pasa a ser la ficha. {@code null} cuando no hay nada pendiente.
	 */
	@Column(name = "program_change_payment_id")
	private UUID programChangePaymentId;

	protected Candidate() {
		// JPA
	}

	public Candidate(UUID personId, UUID admissionConfigId, String folio, boolean llaveMxVerified,
			boolean isFirstChoice, UUID outreachChannelId) {
		this.personId = personId;
		this.admissionConfigId = admissionConfigId;
		this.folio = folio;
		this.status = CandidateStatus.REGISTERED;
		this.llaveMxVerified = llaveMxVerified;
		this.registeredAt = Instant.now();
		this.isFirstChoice = isFirstChoice;
		this.outreachChannelId = outreachChannelId;
		this.isEnabledForInduction = false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPersonId() {
		return personId;
	}

	public UUID getFichaPaymentId() {
		return fichaPaymentId;
	}

	/**
	 * Links the ficha's finance charge once; a different second charge is refused.
	 */
	public void linkFichaPayment(UUID paymentId) {
		if (this.fichaPaymentId != null && !this.fichaPaymentId.equals(paymentId)) {
			throw new IllegalStateException("La ficha ya está ligada a otro cargo: " + this.fichaPaymentId);
		}
		this.fichaPaymentId = paymentId;
	}

	public UUID getAdmissionConfigId() {
		return admissionConfigId;
	}

	public String getFolio() {
		return folio;
	}

	public CandidateStatus getStatus() {
		return status;
	}

	public boolean isLlaveMxVerified() {
		return llaveMxVerified;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public UUID getSelectedBy() {
		return selectedBy;
	}

	public Instant getSelectedAt() {
		return selectedAt;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public boolean isFirstChoice() {
		return isFirstChoice;
	}

	public UUID getOutreachChannelId() {
		return outreachChannelId;
	}

	/**
	 * Corrige los datos de la selección que no afectan el cobro: el canal por
	 * el que se enteró y si la carrera es su primera opción. La carrera en sí
	 * no se cambia aquí (requiere recotizar la ficha).
	 */
	public void updateSelection(UUID outreachChannelId, boolean isFirstChoice) {
		this.outreachChannelId = outreachChannelId;
		this.isFirstChoice = isFirstChoice;
	}

	/** La carrera (configuración de admisión) a la que se cambia el candidato. */
	public void changeAdmissionConfig(UUID admissionConfigId) {
		this.admissionConfigId = admissionConfigId;
	}

	public UUID getProgramChangePaymentId() {
		return programChangePaymentId;
	}

	/** El cargo que se cobra hoy: la diferencia pendiente si la hay, si no la ficha. */
	public UUID collectingPaymentId() {
		return programChangePaymentId != null ? programChangePaymentId : fichaPaymentId;
	}

	/** El cambio de carrera dejó una diferencia por pagar en {@code paymentId}. */
	public void awaitProgramChangePayment(UUID paymentId) {
		this.programChangePaymentId = paymentId;
	}

	/**
	 * El cargo de la carrera nueva quedó pagado (con el saldo a favor, o con el
	 * pago de la diferencia): ahora es la ficha y ya no hay nada pendiente.
	 */
	public void adoptFichaPayment(UUID paymentId) {
		this.fichaPaymentId = paymentId;
		this.programChangePaymentId = null;
	}

	public boolean isEnabledForInduction() {
		return isEnabledForInduction;
	}

	/**
	 * Marks the candidate's ficha as paid ({@code REGISTERED → PAID}), per
	  * {@code 03-admision.md} — the ficha's finance charge must already be settled
	 * (the use case enforces the invariant: no {@code PAID} candidate without a
	 * paid or exempt charge). Idempotent: re-invoking on an already
	 * {@code PAID} candidate is a no-op.
	 */
	public void markPaid() {
		if (this.status == CandidateStatus.PAID) {
			return;
		}
		this.status = CandidateStatus.PAID;
		this.paidAt = Instant.now();
	}

	/**
	 * The calendar date the ficha was registered, in the given zone. It is day 0
	 * of the payment window: {@code registeredAt} is a UTC {@code Instant}, but
	 * the window is counted in the admission calendar, so a registration just
	 * before midnight local must not roll over to the next day.
	 */
	public LocalDate registeredOn(ZoneId zone) {
		return registeredAt.atZone(zone).toLocalDate();
	}

	/**
	 * Last day the ficha can be paid: day 0 ({@link #registeredOn}) plus the
	 * configured deadline. The window closes at 23:59:59 that day, so callers
	 * compare {@code today.isAfter(paymentDeadline(...))} — on the deadline
	 * itself the ficha is still payable. The deadline is derived, never stored:
	 * there is no column, and changing {@code deadline-days} re-reads every ficha.
	 */
	public LocalDate paymentDeadline(ZoneId zone, int deadlineDays) {
		return registeredOn(zone).plusDays(deadlineDays);
	}

	/**
	 * Moves the ficha to {@code PAYMENT_EXPIRED} ("no pago") when its window
	 * lapsed without payment, releasing the CURP lock. Only {@code REGISTERED}
	 * can expire: a {@code PAID} ficha is final and every later status is past
	 * this point of the lifecycle. Returns whether the status changed, so the
	 * caller only saves real writes.
	 */
	public boolean markPaymentExpired() {
		if (this.status != CandidateStatus.REGISTERED) {
			return false;
		}
		this.status = CandidateStatus.PAYMENT_EXPIRED;
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Candidate candidate)) {
			return false;
		}
		return id != null && id.equals(candidate.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}