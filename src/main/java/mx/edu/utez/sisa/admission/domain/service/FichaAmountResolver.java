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
 * the cost of the chosen program's {@code ACTIVE} {@code ENROLLMENT} payment
 * concept on the given date — never a static config value, never a
 * client-supplied number.
 *
 * <p>Plain domain component (no framework annotations) so both the registration
 * flow ({@code RegisterCandidateUseCaseImpl}, which persists the amount on the
 * ticket) and the public quote endpoint that feeds the registration wizard's
 * review step ({@code GetFichaAmountUseCaseImpl}) apply the very same rule.
 *
 * <p>Resolution is STRICT — exactly one active enrollment concept must exist:
 * zero → {@link FichaPaymentConceptNotFoundException}, more than one →
 * {@link AmbiguousFichaPaymentConceptException} (both projected as {@code 409
 * Conflict}: the requested program exists, it just cannot be priced as things
 * stand). Silently picking one of several would price an admission ticket
 * arbitrarily, so both cases fail loud.
 *
 * <p>An empty result is further disambiguated by re-querying without the
 * concept's availability window, so "the period is over" is reported as
 * {@link PaymentConceptExpiredException} with the date, and only a genuinely
 * absent concept produces {@code FichaPaymentConceptNotFoundException}.
 *
 * <p>Two entry points, one rule: {@link #resolve} prices the ficha at
 * registration, {@link #requirePayableOn} re-checks the window when the payment
 * is actually attempted. They share the resolution above so the two can never
 * disagree about whether a concept is sellable.
 */
public class FichaAmountResolver {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final PaymentConceptQueryPort paymentConceptQueryPort;

	public FichaAmountResolver(PaymentConceptQueryPort paymentConceptQueryPort) {
		this.paymentConceptQueryPort = paymentConceptQueryPort;
	}

	/**
	 * @param programId the program whose enrollment concept prices the ficha
	 * @param onDate    the date the concept must be active on (the registration
	 *                  date; concept windows are period-scoped)
	 */
	public FichaAmount resolve(UUID programId, LocalDate onDate) {
		PaymentConceptQueryPort.FichaConcept concept = requireSingleActiveConcept(programId, onDate);
		BigDecimal amount = concept.isExternal() ? concept.costExternal() : concept.cost();
		return new FichaAmount(amount, concept.name());
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
	 * <p>Deliberately returns nothing and re-prices nothing. The amount charged
	 * is frozen on {@code admission_payment.amount} at registration, and stays
	 * frozen: re-reading the catalog cost here would let a mid-period price edit
	 * change what an already-issued ticket costs, and would let a cost edit
	 * between registration and payment decide whether the payment goes through
	 * at all.
	 */
	public void requirePayableOn(UUID programId, LocalDate onDate) {
		requireSingleActiveConcept(programId, onDate);
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
	 * @param amount     what the applicant must pay ({@code costExternal} for an
	 *                   external concept, {@code cost} otherwise)
	 * @param conceptName the catalog concept's name, shown to the applicant
	 */
	public record FichaAmount(BigDecimal amount, String conceptName) {
	}
}
