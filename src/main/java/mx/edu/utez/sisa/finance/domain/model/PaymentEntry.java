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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Abono registered against a {@link Payment}
 * ({@code er-08-pagos.md} — {@code payment_entry}).
 *
 * <p>An abono is append-only history: it is never edited nor deleted, and it
 * never touches a {@link PaymentBenefit} (R8). The payment's running
 * {@code amountPaid}/{@code status} are derived from the sum of these rows,
 * not from a counter maintained in two places.
 *
 * <p>{@code isPhysical} marks a ventanilla collection (corte de caja, R13 —
 * Fase 5); {@code collectedById}/{@code collectedByName} say who took the
 * money. Online charges carry {@code gateway}/{@code externalOrderId} and a
 * {@link PaymentGatewayStatus}; cash abonos leave all of those {@code null}.
 */
@Entity
@Table(name = "payment_entry", indexes = @Index(name = "ix_entry_payment", columnList = "payment_id"))
public class PaymentEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentEntryMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentChannel channel;

	@Column(length = 20)
	private String gateway;

	@Column(name = "external_order_id", length = 80)
	private String externalOrderId;

	@Column(name = "link_id")
	private UUID linkId;

	@Column(length = 120)
	private String reference;

	@Column(name = "receipt_number", length = 40)
	private String receiptNumber;

	@Column(name = "is_physical", nullable = false)
	private boolean isPhysical;

	@Column(name = "collected_by")
	private UUID collectedById;

	@Column(name = "collected_by_name", length = 150)
	private String collectedByName;

	@Column(name = "paid_at", nullable = false)
	private Instant paidAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "gateway_status")
	private PaymentGatewayStatus gatewayStatus;

	protected PaymentEntry() {
		// JPA
	}

	public PaymentEntry(UUID paymentId, BigDecimal amount, PaymentEntryMethod method, PaymentChannel channel,
			String gateway, String externalOrderId, UUID linkId, String reference, String receiptNumber,
			boolean isPhysical, UUID collectedById, String collectedByName, Instant paidAt,
			PaymentGatewayStatus gatewayStatus) {
		this.paymentId = paymentId;
		this.amount = amount;
		this.method = method;
		this.channel = channel;
		this.gateway = gateway;
		this.externalOrderId = externalOrderId;
		this.linkId = linkId;
		this.reference = reference;
		this.receiptNumber = receiptNumber;
		this.isPhysical = isPhysical;
		this.collectedById = collectedById;
		this.collectedByName = collectedByName;
		this.paidAt = paidAt;
		this.gatewayStatus = gatewayStatus;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public PaymentEntryMethod getMethod() {
		return method;
	}

	public PaymentChannel getChannel() {
		return channel;
	}

	public String getGateway() {
		return gateway;
	}

	public String getExternalOrderId() {
		return externalOrderId;
	}

	public UUID getLinkId() {
		return linkId;
	}

	public String getReference() {
		return reference;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public boolean isPhysical() {
		return isPhysical;
	}

	public UUID getCollectedById() {
		return collectedById;
	}

	public String getCollectedByName() {
		return collectedByName;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public PaymentGatewayStatus getGatewayStatus() {
		return gatewayStatus;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PaymentEntry that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
