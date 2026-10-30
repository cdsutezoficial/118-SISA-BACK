package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;

/**
 * The ficha's charge, as {@code finance} owns it.
 *
 * <p>Since the 2026-10-21 unification the money of a ficha lives in
 * {@code finance.payment}: a charge like any other, of the program's catalog
 * {@code ADMISSION} concept (its real {@code code}, never a hard-coded label),
 * anchored to the candidate. Since 2026-10-23 there is no
 * {@code admission_payment} table at all: the charge also carries the ficha's
 * reference and deadline, its abonos carry the receipt, and the EVO attempts
 * live in finance's {@code checkout_attempt}. The candidate links to the charge
 * by {@code Candidate.fichaPaymentId}; {@link #findCharge} reads it back.
 *
 * <p>The charge has four moments, one method each:
 * <ul>
 * <li>{@link #raiseFichaDebt} — at registration, a header with no amount;</li>
 * <li>{@link #quote} — whenever someone looks at it, priced live (tarifa
 * vigente del concepto − descuento vigente), nothing written;</li>
 * <li>{@link #freeze} — when the applicant clicks "Pagar", the live price is
 * photographed on the charge and that exact amount goes to EVO;</li>
 * <li>{@link #settle} — when the money is confirmed, booked with the numbers
 * of the attempt that captured.</li>
 * </ul>
 * Plus {@link #cancel} when the ficha lapses unpaid.
 *
 * <p>Finance types do not leak past the adapter: this port speaks admission's
 * own records.
 */
public interface FichaPaymentPort {

	/**
	 * Raises the ficha's charge as a header (no amount). Idempotent: an open
	 * charge of the same concept for the candidate is returned instead.
	 *
	 * @return the charge's id ({@code finance.payment.id})
	 */
	UUID raiseFichaDebt(RaiseFichaDebtCommand command);

	/** The ficha's header as finance holds it; empty when the charge does not exist. */
	Optional<FichaCharge> findCharge(UUID financePaymentId);

	/** The charge priced live; nothing is written. */
	FichaQuote quote(UUID financePaymentId);

	/** The live price, photographed on the charge right before charging it. */
	FichaQuote freeze(UUID financePaymentId);

	/**
	 * Books the payment with the numbers the applicant was charged
	 * ({@code charged}, the snapshot of the checkout attempt that captured), or
	 * with the live price frozen now when {@code charged} is {@code null}.
	 */
	SettledFicha settle(SettleFichaCommand command);

	/** Cancels the charge if it is still open and unpaid; no-op otherwise. */
	void cancel(UUID financePaymentId);

	// ── Cambio de carrera ────────────────────────────────────────────────────

	/** Sin pagar: el precio vivo que tendría el mismo cargo en la carrera nueva (no escribe). */
	FichaQuote quoteForProgram(UUID financePaymentId, String conceptCode, UUID programId);

	/** Sin pagar: el mismo cargo pasa a la carrera nueva y conserva su descuento. */
	void reassignProgram(UUID financePaymentId, String conceptCode, UUID programId);

	/** Pagada: los números de {@link #rebill} sin escribir nada. */
	ProgramChangeAmounts previewRebill(UUID fromPaymentId, String conceptCode, UUID programId);

	/**
	 * Pagada: cargo nuevo a la tarifa de la carrera nueva (sin descuento, precio
	 * fijo), pagado con lo que ya se había pagado como saldo a favor.
	 */
	ProgramChangeAmounts rebill(RebillFichaCommand command);

	/** Un cargo de cambio de carrera con sus montos fijos; vacío si no existe. */
	Optional<ProgramChangeCharge> findProgramChangeCharge(UUID financePaymentId);

	/**
	 * @param fromPaymentId el cargo que se deja (el pagado, o una diferencia
	 *                      anterior aún sin pagar, que se cancela)
	 * @param dueAt         fecha límite para pagar la diferencia
	 * @param reference     referencia con la que se paga la diferencia
	 * @param actingUserId  quién hizo el cambio
	 */
	record RebillFichaCommand(UUID fromPaymentId, String conceptCode, UUID programId, Instant dueAt,
			String reference, UUID actingUserId) {
	}

	/**
	 * @param financePaymentId el cargo nuevo; {@code null} en la vista previa
	 * @param newAmount        tarifa de la carrera nueva, sin descuento
	 * @param alreadyPaid      lo que había recibido el cargo que se deja
	 * @param otherCredit      saldo a favor que ya tenía y también se aplica
	 * @param toPay            lo que falta pagar ("debe pagar más")
	 * @param creditLeft       lo que sobra como saldo a favor
	 */
	record ProgramChangeAmounts(UUID financePaymentId, BigDecimal newAmount, BigDecimal alreadyPaid,
			BigDecimal otherCredit, BigDecimal toPay, BigDecimal creditLeft) {

		public boolean settled() {
			return toPay.signum() == 0;
		}
	}

	/**
	 * @param newAmount  tarifa de la carrera nueva
	 * @param carried    lo que se le abonó con saldo a favor
	 * @param toPay      lo que falta pagar
	 * @param paid       ya quedó pagado
	 * @param cancelled  se reemplazó por otro cambio de carrera
	 */
	record ProgramChangeCharge(UUID financePaymentId, String referenceNumber, BigDecimal newAmount,
			BigDecimal carried, BigDecimal toPay, Instant dueAt, boolean paid, boolean cancelled, Instant paidAt,
			String receiptNumber) {
	}

	/**
	 * @param candidateId the applicant — the charge's anchor
	 * @param conceptCode the catalog {@code PaymentConcept.code} of the
	 *                    program's ADMISSION concept
	 * @param programId   the program that prices it
	 * @param dueAt       the ficha's payment deadline
	 * @param referenceNumber the reference the applicant pays with
	 */
	record RaiseFichaDebtCommand(UUID candidateId, String conceptCode, UUID programId, Instant dueAt,
			String referenceNumber) {

	}

	/**
	 * The ficha as a charge: what used to be the {@code admission_payment} row.
	 *
	 * @param status        {@code PAID} once the charge is {@code PAID} or
	 *                      {@code EXEMPT}; {@code PENDING} otherwise
	 * @param cancelled     the charge was cancelled (the ficha lapsed unpaid)
	 * @param dueAt         the ficha's deadline (end of day, admission zone)
	 * @param paidAt        when the money (or the exemption) was booked
	 * @param receiptNumber the receipt of that booking
	 */
	record FichaCharge(UUID financePaymentId, String referenceNumber,
			AdmissionPaymentStatus status, boolean cancelled,
			Instant dueAt, Instant createdAt, Instant paidAt, String receiptNumber) {

		public boolean isPaid() {
			return status == AdmissionPaymentStatus.PAID;
		}

		/** The deadline as a calendar day in {@code zone}; {@code null} when none. */
		public LocalDate registrationDeadline(ZoneId zone) {
			return dueAt == null ? null : dueAt.atZone(zone).toLocalDate();
		}
	}

	/**
	 * @param financePaymentId the charge
	 * @param charged          what the applicant was charged, or {@code null}
	 * @param online           {@code true} for EVO, {@code false} for caja
	 * @param externalOrderId  the EVO order, when online
	 * @param receiptNumber    the receipt the confirmation issued
	 * @param reference        the ficha's reference
	 * @param collectedById    acting user, when known
	 * @param collectedByName  display name
	 */
	record SettleFichaCommand(UUID financePaymentId, ChargedAmounts charged, boolean online, String externalOrderId,
			String receiptNumber, String reference, UUID collectedById, String collectedByName) {
	}

	/**
	 * The numbers one checkout charged with.
	 */
	record ChargedAmounts(UUID rateId, BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue,
			UUID benefitId) {
	}

	/**
	 * "Cuesta {@code originalAmount} − descuento {@code discountAmount} =
	 * {@code amountDue}".
	 *
	 * @param exempt         {@code true} when a discount covers the whole tariff
	 * @param live           {@code true} when computed now (charge still open)
	 * @param discountReason why the discount was granted, shown on the ficha
	 */
	record FichaQuote(UUID financePaymentId, String conceptCode, String conceptName, UUID rateId,
			BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue, UUID benefitId,
			boolean exempt, boolean live, String discountReason) {

		public ChargedAmounts charged() {
			return new ChargedAmounts(rateId, originalAmount, discountAmount, amountDue, benefitId);
		}
	}

	/**
	 * @param amountPaid what the charge received
	 */
	record SettledFicha(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amountDue,
			BigDecimal amountPaid, UUID benefitId, boolean exempt) {
	}
}
