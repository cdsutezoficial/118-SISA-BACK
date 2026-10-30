package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Historial de un cambio de carrera hecho por Servicios Escolares desde la
 * ficha: de qué carrera a cuál, quién y cuándo, y con qué montos.
 *
 * <p>Sin pagar: {@code fichaPaymentId} es el mismo cargo, que se movió de
 * carrera, y {@code newAmount} es lo que queda por pagar con el descuento.
 * Pagada: {@code fichaPaymentId} es el cargo pagado que se deja como historial,
 * {@code newPaymentId} el de la carrera nueva, {@code toPay} la diferencia y
 * {@code creditLeft} el saldo a favor que sobró. Solo se inserta, nunca se edita.
 */
@Entity
@Table(name = "candidate_program_change", indexes = @Index(name = "ix_program_change_candidate", columnList = "candidate_id"))
public class CandidateProgramChange {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "candidate_id", nullable = false)
	private UUID candidateId;

	@Column(name = "from_config_id", nullable = false)
	private UUID fromConfigId;

	@Column(name = "to_config_id", nullable = false)
	private UUID toConfigId;

	/** {@code true} si la ficha ya estaba pagada al cambiar. */
	@Column(name = "was_paid", nullable = false)
	private boolean wasPaid;

	@Column(name = "ficha_payment_id")
	private UUID fichaPaymentId;

	@Column(name = "new_payment_id")
	private UUID newPaymentId;

	@Column(name = "previous_amount", precision = 12, scale = 2)
	private BigDecimal previousAmount;

	@Column(name = "paid_amount", precision = 12, scale = 2)
	private BigDecimal paidAmount;

	@Column(name = "new_amount", precision = 12, scale = 2)
	private BigDecimal newAmount;

	@Column(name = "to_pay", precision = 12, scale = 2)
	private BigDecimal toPay;

	@Column(name = "credit_left", precision = 12, scale = 2)
	private BigDecimal creditLeft;

	@Column(name = "changed_by")
	private UUID changedBy;

	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;

	protected CandidateProgramChange() {
		// JPA
	}

	public CandidateProgramChange(UUID candidateId, UUID fromConfigId, UUID toConfigId, boolean wasPaid,
			UUID fichaPaymentId, UUID newPaymentId, BigDecimal previousAmount, BigDecimal paidAmount,
			BigDecimal newAmount, BigDecimal toPay, BigDecimal creditLeft, UUID changedBy, Instant changedAt) {
		this.candidateId = candidateId;
		this.fromConfigId = fromConfigId;
		this.toConfigId = toConfigId;
		this.wasPaid = wasPaid;
		this.fichaPaymentId = fichaPaymentId;
		this.newPaymentId = newPaymentId;
		this.previousAmount = previousAmount;
		this.paidAmount = paidAmount;
		this.newAmount = newAmount;
		this.toPay = toPay;
		this.creditLeft = creditLeft;
		this.changedBy = changedBy;
		this.changedAt = changedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getFromConfigId() {
		return fromConfigId;
	}

	public UUID getToConfigId() {
		return toConfigId;
	}

	public boolean isWasPaid() {
		return wasPaid;
	}

	public UUID getFichaPaymentId() {
		return fichaPaymentId;
	}

	public UUID getNewPaymentId() {
		return newPaymentId;
	}

	public BigDecimal getPreviousAmount() {
		return previousAmount;
	}

	public BigDecimal getPaidAmount() {
		return paidAmount;
	}

	public BigDecimal getNewAmount() {
		return newAmount;
	}

	public BigDecimal getToPay() {
		return toPay;
	}

	public BigDecimal getCreditLeft() {
		return creditLeft;
	}

	public UUID getChangedBy() {
		return changedBy;
	}

	public Instant getChangedAt() {
		return changedAt;
	}
}
