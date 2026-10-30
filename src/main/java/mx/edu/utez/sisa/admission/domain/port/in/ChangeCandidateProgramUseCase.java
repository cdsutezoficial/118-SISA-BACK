package mx.edu.utez.sisa.admission.domain.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Cambio de carrera de un candidato desde la edición de la ficha (Servicios
 * Escolares).
 *
 * <p>El camino del candidato es recto: lo que sigue después del cambio va sobre
 * la carrera nueva.
 * <ul>
 * <li><b>Sin pagar:</b> el mismo cargo de la ficha pasa a la carrera nueva y
 * conserva su descuento (100 % o parcial), que se aplica sobre la tarifa nueva.</li>
 * <li><b>Pagada:</b> el cargo pagado queda como historial con su carrera y su
 * tarifa. El descuento ya se usó: la carrera nueva se cobra a tarifa completa
 * y lo pagado se abona como saldo a favor. Si cuesta más, "debe pagar más" la
 * diferencia en el portal; si cuesta menos, el sobrante queda como saldo a
 * favor. El candidato sigue {@code PAID} y conserva su lugar.</li>
 * </ul>
 * No se permite con el candidato inscrito ({@code ENROLLED}), con la ficha
 * vencida, ni con un intento de pago en curso.
 */
public interface ChangeCandidateProgramUseCase {

	/** Los montos del cambio sin guardar nada. */
	ProgramChangePreview preview(UUID candidateId, UUID admissionConfigId);

	/** Aplica el cambio. Misma carrera que la actual: no hace nada. */
	void change(UUID candidateId, UUID admissionConfigId, UUID actingUserId);

	/** Si el candidato puede cambiar de carrera en su estado actual. */
	boolean isAllowed(UUID candidateId);

	/** Por qué no puede cambiar de carrera ahora; vacío si sí puede. */
	Optional<String> blockedReason(UUID candidateId);

	enum Mode {
		/** La ficha no se ha pagado: se mueve el mismo cargo. */
		UNPAID,
		/** La ficha ya se pagó: cargo nuevo, se abona lo pagado. */
		PAID
	}

	/**
	 * @param previousAmount   sin pagar: lo que debe hoy; pagada: la tarifa con la que se cobró
	 * @param alreadyPaid      lo que ya pagó (pagada) o 0 (sin pagar)
	 * @param otherCredit      saldo a favor que ya tenía y se aplica también
	 * @param newOriginalAmount tarifa de la carrera nueva
	 * @param newDiscountAmount descuento que se respeta (solo sin pagar)
	 * @param newAmount        lo que cuesta la carrera nueva para él
	 * @param toPay            lo que debe pagar después del cambio
	 * @param creditLeft       saldo a favor que le queda
	 * @param quotaAvailable   la carrera nueva tiene lugar (solo cuenta si ya pagó)
	 * @param newPaymentDeadline sin pagar: hasta cuándo puede pagar en la carrera
	 *                         nueva; pagada con diferencia: hasta cuándo puede pagarla
	 * @param paymentWindowClosed sin pagar: el plazo de pago en la carrera nueva ya
	 *                         pasó, así que la ficha vencerá sin poder pagarse
	 */
	record ProgramChangePreview(UUID candidateId, UUID fromConfigId, String fromProgramName, UUID toConfigId,
			String toProgramName, Mode mode, BigDecimal previousAmount, BigDecimal alreadyPaid,
			BigDecimal otherCredit, BigDecimal newOriginalAmount, BigDecimal newDiscountAmount, BigDecimal newAmount,
			BigDecimal toPay, BigDecimal creditLeft, boolean quotaAvailable, LocalDate newPaymentDeadline,
			boolean paymentWindowClosed) {
	}
}
