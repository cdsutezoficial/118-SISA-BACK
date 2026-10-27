package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Applies a person's available saldo a favor to one adeudo (R12, Fase 2 §5.1).
 * The credit is of the <b>person</b>, so it can settle <b>any</b> concept, and
 * both the person and SE/Finanzas can trigger it — no supervisor approval.
 * The flow consumes {@code AVAILABLE} credits oldest-first until the adeudo's
 * remaining balance is covered; anything left over stays available.
 */
public interface ApplyCreditToPaymentUseCase {

	CreditApplicationResult applyCredit(ApplyCreditCommand command);

	/**
	 * @param paymentId     required — the adeudo to settle
	 * @param channel       required — the surface that applied it
	 * @param actingUserId  required — who applied it (owner or staff, from JWT)
	 */
	record ApplyCreditCommand(UUID paymentId, PaymentChannel channel, UUID actingUserId) {
	}

	/**
	 * @param payment       the adeudo after the application
	 * @param creditApplied how much saldo a favor was consumed (0 = none)
	 * @param appliedCreditIds the credits consumed, in the order they were used
	 */
	record CreditApplicationResult(CreatePaymentUseCase.PaymentResult payment, BigDecimal creditApplied,
			List<UUID> appliedCreditIds) {
	}
}
