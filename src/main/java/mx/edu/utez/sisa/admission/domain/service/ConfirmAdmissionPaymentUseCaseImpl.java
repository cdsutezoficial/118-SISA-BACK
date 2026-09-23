package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>The receipt number ({@code REC-{yyyyMMdd}-{folioSeq}}) is generated here,
 * mirroring the payment reference format. Email dispatch is the web layer's
 * concern (best-effort notification, never part of this transaction).
 */
public class ConfirmAdmissionPaymentUseCaseImpl implements ConfirmAdmissionPaymentUseCase {

	private final CandidateRepository candidateRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	public ConfirmAdmissionPaymentUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository) {
		this.candidateRepository = candidateRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
	}

	@Override
	@Transactional
	public ConfirmPaymentResult confirm(UUID candidateId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));

		if (!payment.markPaid(generateReceipt(candidate.getFolio()))) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}

		candidate.markPaid();
		admissionPaymentRepository.save(payment);
		candidateRepository.save(candidate);

		return new ConfirmPaymentResult(candidate.getId(), candidate.getFolio(), candidate.getStatus(),
				payment.getReferenceNumber(), payment.getAmount(), payment.getPaidAt(), payment.getReceiptNumber());
	}

	/** {@code REC-{yyyyMMdd}-{folioSeq}} — same derivation as the payment reference. */
	private static String generateReceipt(String folio) {
		String seq = folio.substring(folio.lastIndexOf('-') + 1);
		String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		return String.format("REC-%s-%s", today, seq);
	}
}