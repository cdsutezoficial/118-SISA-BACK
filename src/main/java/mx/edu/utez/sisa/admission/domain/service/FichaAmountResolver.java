package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 */
public class FichaAmountResolver {

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
		List<PaymentConceptQueryPort.FichaConcept> concepts = paymentConceptQueryPort
				.findActiveEnrollmentForProgram(programId, onDate);
		if (concepts.isEmpty()) {
			throw new FichaPaymentConceptNotFoundException(
					"No existe un concepto de ENROLLMENT activo para el programa: " + programId);
		}
		if (concepts.size() > 1) {
			throw new AmbiguousFichaPaymentConceptException(
					"Existen varios conceptos de ENROLLMENT activos para el programa: " + programId);
		}
		PaymentConceptQueryPort.FichaConcept concept = concepts.get(0);
		BigDecimal amount = concept.isExternal() ? concept.costExternal() : concept.cost();
		return new FichaAmount(amount, concept.name());
	}

	/**
	 * @param amount     what the applicant must pay ({@code costExternal} for an
	 *                   external concept, {@code cost} otherwise)
	 * @param conceptName the catalog concept's name, shown to the applicant
	 */
	public record FichaAmount(BigDecimal amount, String conceptName) {
	}
}
