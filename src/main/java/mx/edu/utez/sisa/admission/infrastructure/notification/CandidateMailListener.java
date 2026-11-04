package mx.edu.utez.sisa.admission.infrastructure.notification;

import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;

/**
 * Sends the candidate's mails after the change that caused them committed (F3).
 *
 * <p>{@code AFTER_COMMIT} is what keeps the mail out of the payment: it runs only once
 * the payment is durable, and nothing it does can roll it back. {@code fallbackExecution}
 * covers a publisher with no transaction around it. The ficha is read in a fresh
 * read-only transaction, the only kind Spring allows after a commit, and the mail
 * service sends asynchronously and swallows SMTP failures.
 */
@Component
public class CandidateMailListener {

	private static final Logger log = LoggerFactory.getLogger(CandidateMailListener.class);

	private static final String PORTAL_PAYMENT_PATH = "/portal/ficha/pago";

	private final GetCandidateFichaUseCase getCandidateFichaUseCase;

	private final CandidateFichaMailService mailService;

	private final String portalUrl;

	public CandidateMailListener(GetCandidateFichaUseCase getCandidateFichaUseCase,
			CandidateFichaMailService mailService,
			@Value("${sisa.security.password-reset.frontend-base-url:}") String frontendBaseUrl) {
		this.getCandidateFichaUseCase = getCandidateFichaUseCase;
		this.mailService = mailService;
		this.portalUrl = portalUrl(frontendBaseUrl);
	}

	@TransactionalEventListener(fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onPaymentConfirmed(CandidateMailEvents.PaymentConfirmed event) {
		ConfirmPaymentResult result = event.result();
		FichaData ficha = fichaWithEmail(result.candidateId());
		if (ficha == null) {
			return;
		}
		CandidateFichaMailService.Breakdown breakdown = result.divided() || result.programChange()
				? new CandidateFichaMailService.Breakdown(result.total(), result.previouslyPaid(),
						result.previousReceipt(), result.previousPaidAt(), result.programChange())
				: null;
		// `paidAt` del resultado, no el reloj: el comprobante lleva la fecha en que se
		// confirmó el pago, no la del envío.
		mailService.sendPaymentConfirmation(ficha.email(), fullName(ficha), ficha.folio(), ficha.programName(),
				result.amount(), result.referenceNumber(), result.paidAt(), result.receiptNumber(), breakdown);
	}

	@TransactionalEventListener(fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onDifferenceLeft(CandidateMailEvents.DifferenceLeft event) {
		FichaData ficha = fichaWithEmail(event.candidateId());
		if (ficha == null) {
			return;
		}
		mailService.sendProgramChangeDifference(ficha.email(), fullName(ficha), ficha.folio(), ficha.programName(),
				event.newAmount(), event.alreadyPaid(), event.toPay(), event.dueAt(), portalUrl);
	}

	@TransactionalEventListener(fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public void onCreditLeft(CandidateMailEvents.CreditLeft event) {
		FichaData ficha = fichaWithEmail(event.candidateId());
		if (ficha == null) {
			return;
		}
		mailService.sendCreditLeft(ficha.email(), fullName(ficha), ficha.folio(), ficha.programName(),
				event.credit());
	}

	/** The ficha, or {@code null} when it cannot be mailed; never throws. */
	private FichaData fichaWithEmail(UUID candidateId) {
		try {
			FichaData ficha = getCandidateFichaUseCase.get(candidateId);
			return ficha != null && ficha.email() != null && !ficha.email().isBlank() ? ficha : null;
		} catch (RuntimeException ex) {
			log.warn("No se pudo leer la ficha {} para enviarle correo: {}", candidateId, ex.getMessage());
			return null;
		}
	}

	private static String fullName(FichaData ficha) {
		return ((ficha.firstName() == null ? "" : ficha.firstName()) + " "
				+ (ficha.lastName1() == null ? "" : ficha.lastName1()) + " "
				+ (ficha.lastName2() == null ? "" : ficha.lastName2())).trim();
	}

	/** {@code https://host/SGA/} → {@code https://host/SGA/portal/ficha/pago}; blank → {@code null}. */
	static String portalUrl(String frontendBaseUrl) {
		if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
			return null;
		}
		try {
			URI.create(frontendBaseUrl.trim());
		} catch (IllegalArgumentException ex) {
			return null;
		}
		return frontendBaseUrl.trim().replaceAll("/+$", "") + PORTAL_PAYMENT_PATH;
	}
}
