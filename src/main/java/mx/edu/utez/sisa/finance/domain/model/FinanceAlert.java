package mx.edu.utez.sisa.finance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A payment Finanzas has to review by hand (E4): an authorization the bank never
 * captured, a capture that found no place, or money that settled nothing.
 *
 * <p>One row per kind and order. The nightly reconciliation finds an old
 * authorization every night until it is resolved, and raising it again only moves
 * {@code lastSeenAt}; it never creates a second row nor reopens one already resolved.
 */
@Entity
@Table(name = "finance_alert", uniqueConstraints = @UniqueConstraint(name = "ux_alert_type_order",
		columnNames = { "type", "order_id" }), indexes = @Index(name = "ix_alert_status", columnList = "status, created_at"))
public class FinanceAlert {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private FinanceAlertType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FinanceAlertStatus status;

	@Column(name = "candidate_id")
	private UUID candidateId;

	@Column(name = "payment_id")
	private UUID paymentId;

	@Column(name = "order_id", nullable = false, length = 80)
	private String orderId;

	/** Folio of the ficha, so the list reads without a join. */
	@Column(length = 40)
	private String folio;

	@Column(precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(length = 500)
	private String detail;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "resolved_by")
	private UUID resolvedBy;

	@Column(name = "resolution_note", length = 500)
	private String resolutionNote;

	protected FinanceAlert() {
	}

	public FinanceAlert(FinanceAlertType type, UUID candidateId, UUID paymentId, String orderId, String folio,
			BigDecimal amount, String detail, Instant now) {
		this.type = Objects.requireNonNull(type, "La alerta necesita su tipo");
		this.orderId = Objects.requireNonNull(orderId, "La alerta necesita su orden");
		this.createdAt = Objects.requireNonNull(now, "La alerta necesita su fecha");
		this.status = FinanceAlertStatus.OPEN;
		this.candidateId = candidateId;
		this.paymentId = paymentId;
		this.folio = folio;
		this.amount = amount;
		this.detail = detail;
		this.lastSeenAt = now;
	}

	/** The condition was found again; an open alert records when. */
	public void seenAgain(Instant now) {
		if (status == FinanceAlertStatus.OPEN) {
			lastSeenAt = now;
		}
	}

	/** Marks the alert as reviewed. Resolving it twice keeps the first resolution. */
	public void resolve(UUID userId, String note, Instant now) {
		if (status == FinanceAlertStatus.RESOLVED) {
			return;
		}
		status = FinanceAlertStatus.RESOLVED;
		resolvedBy = userId;
		resolvedAt = now;
		resolutionNote = note == null || note.isBlank() ? null : note.trim();
	}

	public UUID getId() {
		return id;
	}

	public FinanceAlertType getType() {
		return type;
	}

	public FinanceAlertStatus getStatus() {
		return status;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public String getOrderId() {
		return orderId;
	}

	public String getFolio() {
		return folio;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getDetail() {
		return detail;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public UUID getResolvedBy() {
		return resolvedBy;
	}

	public String getResolutionNote() {
		return resolutionNote;
	}
}
