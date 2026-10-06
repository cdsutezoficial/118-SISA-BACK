package mx.edu.utez.sisa.admission.domain.port.in;

/**
 * Time-driven expiry of unpaid fichas: the daily scheduler counterpart to
 * §1.9/§1.12 of the cupo decision. Every candidate still {@code REGISTERED}
 * whose payment window ({@code registeredAt} + {@code deadline-days}, closing
 * at 23:59:59 of day N) has passed without payment moves to
 * {@link mx.edu.utez.sisa.admission.domain.model.CandidateStatus#PAYMENT_EXPIRED}
 * ("no pago"), which releases the CURP lock so the person can register again
 * (§1.10).
 *
 * <p>This is also the safety net for the one case §6 deliberately does not
 * resolve at payment time: an EVO {@code SUCCESS} with no capture is withheld
 * rather than released, and the ficha's own expiry is what eventually frees the
 * slot.
 */
public interface ExpireStaleFichaPaymentsUseCase {

	/**
	 * Expires every {@code REGISTERED} ficha past its deadline, using the
	 * admission clock and zone so the comparison is a calendar-date one and not
	 * an {@code Instant} one.
	 *
	 * @return how many fichas changed to {@code PAYMENT_EXPIRED}
	 */
	int expireOverdue();
}
