package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Single source of truth for the admission-ficha price (Fase 11): the amount is
 * the {@code payment_rate} of the chosen program's {@code ACTIVE}
 * {@code ADMISSION} payment concept on the given date — never a static config
 * value, never a client-supplied number, and never a price carried on the
 * concept itself.
 *
 * <p>Plain domain component (no framework annotations) so both the registration
 * flow ({@code RegisterCandidateUseCaseImpl}, which persists the amount on the
 * ticket) and the public quote endpoint that feeds the registration wizard's
 * review step ({@code GetFichaAmountUseCaseImpl}) apply the very same rule.
 *
 * <p>Resolution is STRICT in two steps, and both fail loud. First, EXACTLY ONE
 * active admission concept must exist: zero →
 * {@link FichaPaymentConceptNotFoundException}, more than one →
 * {@link AmbiguousFichaPaymentConceptException}. Then that concept must have a
 * rate that prices this program on this date: none is also
 * {@link FichaPaymentConceptNotFoundException}. Silently picking one of several
 * concepts, or falling back to some other amount, would price an admission
 * ticket arbitrarily.
 *
 * <p>An empty first step is further disambiguated by re-querying without the
 * concept's availability window, so "the period is over" is reported as
 * {@link PaymentConceptExpiredException} with the date, and only a genuinely
 * absent concept produces {@code FichaPaymentConceptNotFoundException}.
 *
 * <p>Two entry points share the first step: {@link #resolve} prices the ficha —
 * at registration for the initial quote, and again when the applicant starts
 * the checkout, where the live tariff overwrites that quote and becomes the one
 * charged (the price of the click, §1.3). {@link #requirePayableOn} only
 * re-checks the window when the payment is actually attempted and never prices
 * anything, so a cost edit cannot turn the window check into a payment-time
 * failure.
 */
public class FichaAmountResolver {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final PaymentConceptQueryPort paymentConceptQueryPort;

	public FichaAmountResolver(PaymentConceptQueryPort paymentConceptQueryPort) {
		this.paymentConceptQueryPort = paymentConceptQueryPort;
	}

	/**
	 * @param programId the program whose admission concept prices the ficha
	 * @param onDate    the date the concept must be active on (the registration
	 *                  date; concept windows are period-scoped)
	 */
	public FichaAmount resolve(UUID programId, LocalDate onDate) {
		PaymentConceptQueryPort.FichaConcept concept = requireSingleActiveConcept(programId, onDate);
		BigDecimal amount = paymentConceptQueryPort.findActiveRateAmountFor(concept.id(), programId, onDate)
				.orElseThrow(() -> describeMissingRate(concept.name()));
		return new FichaAmount(amount, concept.name());
	}

	/**
	 * The concept exists and may be sold, but nothing in its rate history prices
	 * this program on this date.
	 *
	 * <p>Same {@link FichaPaymentConceptNotFoundException} as an absent concept,
	 * and therefore the same {@code 409 ADMISSION_CONCEPT_NOT_FOUND}, because for
	 * the applicant and for the front the two are the same situation: the
	 * university never configured what this admission costs. The front branches
	 * on the code, not on the message, so splitting them would only add a code
	 * the applicant cannot act on differently. The messages do differ, though,
	 * because the staff who has to fix it can: one names a concept that does
	 * not exist, the other names the one that is missing its price.
	 */
	private static RuntimeException describeMissingRate(String conceptName) {
		return new FichaPaymentConceptNotFoundException(
				"El concepto de pago \"" + conceptName
						+ "\" no tiene una tarifa configurada para esta carrera. Contacta a la universidad.");
	}

	/**
	 * Asserts the ficha's tuition concept can be paid on {@code onDate}, without
	 * pricing anything.
	 *
	 * <p>This is the payment-side half of the catalog's availability window. The
	 * window is already consulted at registration, but a registration is not a
	 * payment: without this, a ticket issued on the last day of the period stays
	 * payable for as long as the applicant keeps clicking "Pagar en línea", long
	 * after Conceptos de Pago says the period is over.
	 *
	 * <p>Deliberately returns nothing and re-prices nothing, and deliberately
	 * does not look for a rate: pricing is {@link #resolve}'s job, which the
	 * checkout runs itself right after this gate, so the two stay apart and a
	 * window check cannot fail because a rate was edited, nor a cost edit decide
	 * whether the period is open. A concept that still exists and is still
	 * sellable is enough to open the payment.
	 */
	public void requirePayableOn(UUID programId, LocalDate onDate) {
		requireSingleActiveConcept(programId, onDate);
	}

	/**
	 * The date on which online payment of the ficha stops being accepted, or
	 * {@code null} when the catalog sets no closing date (or the program has no
	 * tuition concept to ask).
	 *
	 * <p>Read live, from the catalog, on every request — never snapshotted onto
	 * the ticket. That is the whole point: extending a period is done by editing
	 * the concept in Conceptos de Pago, and if the date were frozen at
	 * registration a late applicant could not be given more time without a
	 * per-ficha override. It also means this is the same value
	 * {@link #requirePayableOn} enforces, so a screen that shows it is showing
	 * what the system will actually do.
	 *
	 * <p>Deliberately returns {@code null} instead of throwing, and deliberately
	 * does not check the date it returns against today. The caller is a screen
	 * showing a pending ficha, and the interesting case is precisely the one
	 * where the window has already closed: the applicant still has to be able to
	 * open the ficha, see what they owe and read what the real date was. A method
	 * that refused to answer after the fact would leave the UI with nothing to
	 * show and a 409 as the only explanation.
	 *
	 * <p>{@code null} is genuinely ambiguous between "no end date configured" and
	 * "no such program", and the two are reported the same way on purpose: in
	 * both, the honest thing for the screen to do is omit the row rather than
	 * invent a date.
	 *
	 * <p>Ambiguity is not ambiguous here. If a program somehow has more than one
	 * active tuition concept there is no single window to report, and the strict
	 * rule that {@link #requirePayableOn} enforces will already be refusing the
	 * payment — so this returns {@code null} and lets that refusal be the
	 * explanation, instead of picking one concept's date and displaying it as
	 * though it governed.
	 */
	public LocalDate paymentClosesOn(UUID programId) {
		List<PaymentConceptQueryPort.FichaConcept> concepts = paymentConceptQueryPort
				.findActiveEnrollmentForProgram(programId);
		if (concepts.size() != 1) {
			return null;
		}
		return concepts.get(0).availableUntil();
	}

	/**
	 * The strict "exactly one" rule, shared by pricing and by the payment-window
	 * check so both agree on what a well-formed catalog looks like.
	 */
	private PaymentConceptQueryPort.FichaConcept requireSingleActiveConcept(UUID programId, LocalDate onDate) {
		List<PaymentConceptQueryPort.FichaConcept> concepts = paymentConceptQueryPort
				.findActiveEnrollmentForProgram(programId, onDate);
		if (concepts.isEmpty()) {
			throw describeWhyNothingPricedOrPayable(programId, onDate);
		}
		if (concepts.size() > 1) {
			throw new AmbiguousFichaPaymentConceptException(
					"Esta carrera tiene más de un concepto de inscripción activo. Contacta a la universidad.");
		}
		return concepts.get(0);
	}

	/**
	 * Decides which of the two failures the applicant is actually looking at.
	 *
	 * <p>The fallback query drops the window. If it also comes back empty, the
	 * program genuinely has no tuition concept and that is a catalog problem for
	 * the staff to fix. If it finds one, the concept is there and simply is not
	 * sellable today, so the message names the boundary that was missed — which
	 * is the only version of this answer an applicant can do anything with.
	 */
	private RuntimeException describeWhyNothingPricedOrPayable(UUID programId, LocalDate onDate) {
		List<PaymentConceptQueryPort.FichaConcept> ignoringWindow = paymentConceptQueryPort
				.findActiveEnrollmentForProgram(programId);
		if (ignoringWindow.isEmpty()) {
			return new FichaPaymentConceptNotFoundException(
					"Esta carrera no tiene un concepto de inscripción activo. Contacta a la universidad.");
		}
		if (ignoringWindow.size() > 1) {
			return new AmbiguousFichaPaymentConceptException(
					"Esta carrera tiene más de un concepto de inscripción activo. Contacta a la universidad.");
		}
		LocalDate availableFrom = ignoringWindow.get(0).availableFrom();
		LocalDate availableUntil = ignoringWindow.get(0).availableUntil();
		if (availableFrom != null && onDate.isBefore(availableFrom)) {
			return new PaymentConceptExpiredException(
					"El pago de la ficha de esta carrera abre el " + format(availableFrom) + ".");
		}
		if (availableUntil != null && onDate.isAfter(availableUntil)) {
			return new PaymentConceptExpiredException(
					"El pago de la ficha de esta carrera cerró el " + format(availableUntil) + ".");
		}
		// The window does not actually exclude onDate, so the concept changed
		// under us between the two queries. Reporting "not available" would be a
		// guess; the catalog has to be looked at.
		return new FichaPaymentConceptNotFoundException(
				"El concepto de inscripción de esta carrera no está disponible. Contacta a la universidad.");
	}

	/**
	 * Explicit {@code dd/MM/yyyy} rather than a locale default: the string is
	 * shown to applicants, and the rest of the admission messages already commit
	 * to that format.
	 */
	private static String format(LocalDate date) {
		return DATE_FORMAT.format(date);
	}

	/**
	 * @param amount     what the applicant must pay, resolved from the concept's
	 *                   rate for this program on this date
	 * @param conceptName the catalog concept's name, shown to the applicant
	 */
	public record FichaAmount(BigDecimal amount, String conceptName) {
	}
}
