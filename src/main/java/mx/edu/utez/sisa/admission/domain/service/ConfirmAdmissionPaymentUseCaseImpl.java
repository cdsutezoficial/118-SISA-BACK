package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Payment-confirmation interactor (design:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md},
 * {@code ConfirmAdmissionPaymentUseCase}): marks the ticket payment
 * {@code PAID} and the candidate {@code PAID} in one transaction, then returns
 * the data the web layer needs to render the paid ficha / confirmation email.
 *
 * <p>Validations, in order:
 * <ol>
 * <li>the {@code candidateId} must resolve to a {@code Candidate} and its
 * {@code ADMISSION_FICHA} payment must exist (404,
 * {@code CandidateNotFoundException});</li>
 * <li>the payment must still be {@code PENDING} (409,
 * {@code CandidateAlreadyPaidException}) — an already-paid ficha is a
 * repeat-confirmation, not a new transition.</li>
 * </ol>
 *
 * <p>The receipt number ({@code REC-{yyyyMMdd}-{folioSeq}}) is generated here.
 * It deliberately does <em>not</em> follow the payment reference: that one is
 * {@code REF-{folio}} (decisión 11.1), because a reference has to be recomputable
 * from the ficha alone, while a receipt is minted once at payment time and
 * stamped on an email and a PDF. It still reads {@code LocalDate.now()} from the
 * server's default zone, which the reference no longer does — not worth a format
 * change on strings already printed and mailed, but not something to copy
 * anywhere new. Email dispatch is the web layer's concern (best-effort
 * notification, never part of this transaction).
 */
public class ConfirmAdmissionPaymentUseCaseImpl implements ConfirmAdmissionPaymentUseCase {

	/** Who collected the money, as the receipt shows it: the online checkout or the system itself. */
	private static final String ONLINE_COLLECTOR = "Pago en línea";

	private static final String SYSTEM_COLLECTOR = "Sistema";

	private final CandidateRepository candidateRepository;


	private final FichaPaymentPort fichaPaymentPort;

	private final CheckoutAttemptRepository checkoutAttemptRepository;


	public ConfirmAdmissionPaymentUseCaseImpl(CandidateRepository candidateRepository,
			FichaPaymentPort fichaPaymentPort,
			CheckoutAttemptRepository checkoutAttemptRepository) {
		this.candidateRepository = candidateRepository;
		this.fichaPaymentPort = fichaPaymentPort;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
	}

	@Override
	@Transactional
	public ConfirmPaymentResult confirm(UUID candidateId, String capturedOrderId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		FichaPaymentPort.FichaCharge charge = FichaCharges.of(candidate, fichaPaymentPort)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));
		if (charge.isPaid()) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}

		// Same transaction: the finance charge and the candidate are settled
		// together, or neither is. The numbers booked are the captured
		// attempt's, when there is one; the receipt goes on the abono.
		String receipt = generateReceipt(candidate.getFolio());
		CheckoutAttempt attempt = capturedOrderId == null ? null
				: checkoutAttemptRepository.findByOrderId(capturedOrderId).orElse(null);
		FichaPaymentPort.SettledFicha settled = fichaPaymentPort.settle(new FichaPaymentPort.SettleFichaCommand(
				charge.financePaymentId(), chargedBy(attempt), attempt != null, capturedOrderId, receipt,
				charge.referenceNumber(), null, attempt != null ? ONLINE_COLLECTOR : SYSTEM_COLLECTOR));

		candidate.markPaid();
		candidateRepository.save(candidate);

		FichaPaymentPort.FichaCharge paid = fichaPaymentPort.findCharge(charge.financePaymentId()).orElse(charge);
		return new ConfirmPaymentResult(candidate.getId(), candidate.getFolio(), candidate.getStatus(),
				charge.referenceNumber(), settled.amountPaid(), paid.paidAt() != null ? paid.paidAt() : Instant.now(),
				paid.receiptNumber() != null ? paid.receiptNumber() : receipt);
	}

	/**
	 * The attempt's snapshot, or {@code null} when it has none (no attempt, or
	 * one opened before snapshots existed) — finance then prices live.
	 */
	private static FichaPaymentPort.ChargedAmounts chargedBy(CheckoutAttempt attempt) {
		if (attempt == null || !attempt.hasSnapshot()) {
			return null;
		}
		return new FichaPaymentPort.ChargedAmounts(attempt.getRateId(), attempt.getOriginalAmount(),
				attempt.getDiscountAmount(), attempt.getAmount(), attempt.getBenefitId());
	}

	/** {@code REC-{yyyyMMdd}-{folioSeq}} — minted at payment time; see the class javadoc. */
	private static String generateReceipt(String folio) {
		String seq = folio.substring(folio.lastIndexOf('-') + 1);
		String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		return String.format("REC-%s-%s", today, seq);
	}
}