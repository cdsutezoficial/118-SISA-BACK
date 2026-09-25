package mx.edu.utez.sisa.admission.domain.port.in;

import java.util.UUID;

/**
 * Payment confirmation WITH EVO verification (Fase 5 of the payment plan):
 * before any {@code markPaid}, the gateway must confirm the ficha's online
 * order — {@code result=SUCCESS} and the same amount — otherwise no local
 * "pagado" is produced (no false confirmations). Deadlocks into the existing
 * {@link ConfirmAdmissionPaymentUseCase} for the actual transition, so receipt
 * generation, idempotency (409) and the "no {@code PAID} without paid payment"
 * invariant stay single-sourced.
 *
 * <p>Legacy window path: when the ficha was never initiated online
 * ({@code orderId} not persisted on the payment) and the caller sends no
 * {@code orderId}, confirmation falls back to the plain local confirm (manual
 * window capture, today invoked by Finanzas — role-gating lands in Fase 6).
 */
public interface ConfirmFichaPaymentVerifiedUseCase {

	ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult confirm(UUID candidateId, String orderId);
}