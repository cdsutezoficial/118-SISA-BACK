package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Admission-ticket payment ("pago de ficha de admisión"), per
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}. Created as
 * {@code PENDING} together with its {@link Candidate} by
 * {@code RegisterCandidateUseCase} (the ticket generates a payment reference,
 * an initial amount quote and the registration window's closing date the
 * moment the ficha is created); the amount is re-quoted live when the applicant
 * starts the checkout ({@link #reprice}); transitioned to {@code PAID} by
 * {@code ConfirmAdmissionPaymentUseCase}.
 *
 * <p>One {@code AdmissionPayment} per {@code Candidate} for the
 * {@code ADMISSION_FICHA} concept (identity: {@code candidateId}). The
 * {@code candidateId} FK is a plain {@code UUID} column — {@code Candidate}
 * is the owning aggregate (same bare-FK convention as {@code Candidate#personId}).
 *
 * <p>TEMPORARY DEVIATION: payment confirmation is triggered directly by the
 * public portal's "Pagar en línea" button (no EVO webhook yet) — the EVO
 * Hosted-Checkout integration lands later and will replace this trigger; the
 * aggregate shape already matches the design.
 *
 * <p>EVO Hosted Checkout: when the applicant clicks "Pagar en línea", the
 * checkout use case initiates a gateway session and {@link #registerCheckout}
 * records the gateway {@code order.id} and {@code session.id} on this concept
 * (the "concept that registers the transaction") — Fase 4 of the payment plan.
 * These stay {@code null} while the ticket is only pending (no online session
 * has been created yet).
 */
@Entity
@Table(name = "admission_payment")
public class AdmissionPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "candidate_id", nullable = false)
	private UUID candidateId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdmissionPaymentConcept concept;

	/**
	 * What the applicant is charged for this ficha.
	 *
	 * <p>Written twice. At registration it is a catalog quote as of that day;
	 * the moment the applicant starts the checkout it is overwritten with the
	 * live tariff ({@link #reprice}), because the price that governs is the one
	 * the applicant saw when they clicked to pay (§1.3). Once the ficha is
	 * {@code PAID} the value is frozen: confirmation compares the bank's captured
	 * amount against it.
	 */
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "reference_number", nullable = false, length = 40)
	private String referenceNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false)
	private AdmissionPaymentStatus paymentStatus;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "receipt_number", length = 40)
	private String receiptNumber;

	/**
	 * When this ficha claimed one of its career's quota slots, or {@code null} if
	 * it holds none.
	 *
	 * <p>Nullable on purpose and added without a backfill: the occupancy count is
	 * a query, not a stored number, so there is nothing to migrate — a ficha with
	 * {@code null} simply is not holding a slot, which is the correct state for
	 * every payment that exists today.
	 */
	@Column(name = "checkout_claimed_at")
	private Instant checkoutClaimedAt;

	/**
	 * The registration sales window's closing date, snapshotted when the ficha
	 * was issued: {@code ProgramAdmissionConfig.closesAt}, not a count of days
	 * after registration.
	 *
	 * <p><b>Legacy column name.</b> The column is still called
	 * {@code payment_deadline} and the field deliberately keeps mapping to it.
	 * Renaming a {@code NOT NULL} column is not free under
	 * {@code ddl-auto=update}: Hibernate would add the new column and leave the
	 * old one behind, so every existing row would fail the new column's
	 * {@code NOT NULL} the next time it was written. The misleading name is a
	 * cheaper thing to carry than a migration, and it is confined to this
	 * annotation — everything above it speaks {@code registrationDeadline}.
	 *
	 * <p>This is <em>not</em> the payment deadline. The payment window lives in
	 * {@code PaymentConcept.availableUntil} and is read live, because editing the
	 * concept is how a period gets extended and every pending ficha of that
	 * program has to move with it. See {@code FichaAmountResolver#paymentClosesOn}.
	 */
	@Column(name = "payment_deadline", nullable = false)
	private LocalDate registrationDeadline;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** EVO gateway {@code order.id} (orderId-prefix + folio), set when a Hosted Checkout session is initiated. */
	@Column(name = "order_id", length = 64)
	private String orderId;

	/** EVO gateway {@code session.id}, set when a Hosted Checkout session is initiated. */
	@Column(name = "checkout_session_id", length = 64)
	private String checkoutSessionId;

	protected AdmissionPayment() {
		// JPA
	}

	public AdmissionPayment(UUID candidateId, AdmissionPaymentConcept concept, BigDecimal amount,
			String referenceNumber, LocalDate registrationDeadline) {
		this.candidateId = candidateId;
		this.concept = concept;
		this.amount = amount;
		this.referenceNumber = referenceNumber;
		this.paymentStatus = AdmissionPaymentStatus.PENDING;
		this.registrationDeadline = registrationDeadline;
		this.createdAt = Instant.now();
	}

	/**
	 * Marks this ficha payment as {@code PAID}, stamping when it was paid and
	 * the receipt number. Guards against double-payment: re-invoking after the
	 * first successful payment is a no-op (returns {@code false}).
	 *
	 * @param receiptNumber transaction receipt issued by the payment
	 *                      confirmation ("compra" / manual capture).
	 * @return {@code true} if the transition PENDING → PAID actually happened.
	 */
	public boolean markPaid(String receiptNumber) {
		if (this.paymentStatus == AdmissionPaymentStatus.PAID) {
			return false;
		}
		this.paymentStatus = AdmissionPaymentStatus.PAID;
		this.paidAt = Instant.now();
		this.receiptNumber = receiptNumber;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public AdmissionPaymentConcept getConcept() {
		return concept;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public AdmissionPaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	/**
	 * When the registration window this ficha was issued under closes. See the
	 * field javadoc for why the column is still {@code payment_deadline}.
	 */
	public LocalDate getRegistrationDeadline() {
		return registrationDeadline;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getOrderId() {
		return orderId;
	}

	public String getCheckoutSessionId() {
		return checkoutSessionId;
	}

	/**
	 * Records the EVO Hosted Checkout session created for THIS ficha payment
	 * (the concept that registers the transaction): the gateway {@code order.id}
	 * and {@code session.id}. Only meaningful while {@code PENDING} — a paid
	 * ficha must never be re-registered against a new online session.
	 *
	 * @throws IllegalStateException if the payment is already {@code PAID}
	 *                               (guarded earlier at use-case level → 409).
	 */
	public void registerCheckout(String orderId, String checkoutSessionId) {
		if (this.paymentStatus == AdmissionPaymentStatus.PAID) {
			throw new IllegalStateException("La ficha ya está pagada; no se puede asociar una sesión de pago.");
		}
		this.orderId = orderId;
		this.checkoutSessionId = checkoutSessionId;
	}

	/**
	 * Overwrites the ficha's amount with the tariff the catalog quotes at the
	 * moment the applicant starts paying.
	 *
	 * <p>The value deposited at registration is only a quote: a tariff edited
	 * between issuing and paying must reach the applicant, and the number sent
	 * to the gateway is the one the confirmation later checks against the bank.
	 * Kept out of {@code PAID} fichas, whose amount is what was actually charged.
	 *
	 * @throws IllegalStateException if the ficha is already {@code PAID}
	 */
	public void reprice(BigDecimal amount) {
		if (this.paymentStatus == AdmissionPaymentStatus.PAID) {
			throw new IllegalStateException("La ficha ya está pagada; no se puede re-cotizar.");
		}
		this.amount = amount;
	}

	/**
	 * Marks this ficha as holding one of its career's quota slots.
	 *
	 * <p>Called <em>before</em> the gateway is touched, and committed before the
	 * gateway call, because the moment Evo has an order the money may already be
	 * captured — and a payment that was charged cannot be refused afterwards
	 * without a refund. The claim is what makes "no more than
	 * {@code maxCandidates} fichas" enforceable at that exact point.
	 *
	 * <p>A claim stops counting on its own, with no scheduled cleanup, once the
	 * tuition concept's {@code available_until} has passed: the count is a function
	 * of stored data, so a ficha nobody paid for simply falls out of it.
	 */
	public void claimCheckoutSlot() {
		if (this.paymentStatus != AdmissionPaymentStatus.PENDING) {
			throw new IllegalStateException("Only PENDING payments can claim a checkout slot");
		}
		this.checkoutClaimedAt = Instant.now();
	}

	/**
	 * Gives the slot back immediately after the gateway refused the checkout, so
	 * the quota does not stay held until the payment window closes for an order
	 * that does not exist.
	 */
	public void releaseCheckoutSlot() {
		this.checkoutClaimedAt = null;
	}

	public Instant getCheckoutClaimedAt() {
		return checkoutClaimedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AdmissionPayment that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}