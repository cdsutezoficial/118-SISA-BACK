package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentGatewayStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.SettleDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Settles a charge with the numbers the payer was actually charged (see
 * {@link SettleDebtUseCase}).
 */
public class SettleDebtUseCaseImpl implements SettleDebtUseCase {

	private static final Logger log = LoggerFactory.getLogger(SettleDebtUseCaseImpl.class);

	private final PaymentRepository paymentRepository;

	private final FreezeDebtQuoteUseCaseImpl freezeDebtQuote;

	private final RegisterPaymentEntryUseCase registerPaymentEntryUseCase;

	private final RegisterExemptionUseCase registerExemptionUseCase;

	private final Clock clock;

	public SettleDebtUseCaseImpl(PaymentRepository paymentRepository, FreezeDebtQuoteUseCaseImpl freezeDebtQuote,
			RegisterPaymentEntryUseCase registerPaymentEntryUseCase, RegisterExemptionUseCase registerExemptionUseCase,
			Clock clock) {
		this.paymentRepository = paymentRepository;
		this.freezeDebtQuote = freezeDebtQuote;
		this.registerPaymentEntryUseCase = registerPaymentEntryUseCase;
		this.registerExemptionUseCase = registerExemptionUseCase;
		this.clock = clock;
	}

	@Override
	@Transactional
	public SettledDebt settle(SettleDebtCommand command) {
		validate(command);
		Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.paymentId()));

		if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.EXEMPT) {
			return toResult(payment); // idempotent: already settled
		}
		if (payment.getStatus() == PaymentStatus.CANCELLED) {
			throw new PaymentNotPayableException("El adeudo " + payment.getId() + " está cancelado.");
		}

		applyChargedNumbers(payment, command.snapshot());

		if (payment.getAmountDue().signum() == 0) {
			registerExemptionUseCase.registerExemption(new RegisterExemptionUseCase.RegisterExemptionCommand(
					payment.getId(), command.receiptNumber(), command.reference(), command.collectedById(),
					command.collectedByName()));
			return toResult(reload(payment));
		}

		BigDecimal remaining = payment.getAmountDue().subtract(payment.getAmountPaid());
		if (remaining.signum() <= 0) {
			return toResult(payment);
		}
		registerPaymentEntryUseCase.registerEntry(new RegisterPaymentEntryUseCase.RegisterEntryCommand(
				payment.getId(), remaining, command.method(), command.channel(), command.gateway(),
				command.externalOrderId(), null, command.reference(), command.receiptNumber(),
				command.channel() == PaymentChannel.CASHIER,
				command.collectedById(), command.collectedByName(),
				command.gateway() == null ? null : PaymentGatewayStatus.APPROVED));
		return toResult(reload(payment));
	}

	/**
	 * Makes the charge carry the numbers the payer was charged with.
	 *
	 * <p>With a snapshot (the checkout attempt that captured): if the charge
	 * was frozen differently afterwards — a later checkout with another
	 * discount — the captured attempt's numbers are written back, because those
	 * are the ones the money matches. A charge that already has abonos cannot
	 * be re-priced, so a mismatch there is refused rather than booked wrong.
	 *
	 * <p>Without a snapshot (caja, or a confirmation with no attempt): the live
	 * quote is frozen now, which is what the payer is being asked for.
	 */
	private void applyChargedNumbers(Payment payment, ChargedSnapshot snapshot) {
		if (snapshot == null) {
			if (payment.isRepriceable()) {
				freezeDebtQuote.freeze(payment, LocalDate.now(clock));
			}
			if (!payment.isPriced()) {
				throw new PaymentNotPayableException("El adeudo " + payment.getId() + " no tiene monto cotizado.");
			}
			return;
		}
		boolean sameNumbers = payment.isPriced() && payment.getAmountDue().compareTo(snapshot.amountDue()) == 0
				&& Objects.equals(payment.getBenefitId(), snapshot.benefitId());
		if (sameNumbers) {
			return;
		}
		if (!payment.isRepriceable()) {
			throw new PaymentNotPayableException("El monto cobrado no coincide con el adeudo " + payment.getId()
					+ ", que ya tiene abonos; requiere revisión.");
		}
		if (payment.isPriced()) {
			log.info("Adeudo {} se liquida con el snapshot del intento capturado ({}), no con el último ({}).",
					payment.getId(), snapshot.amountDue(), payment.getAmountDue());
		}
		payment.reprice(snapshot.originalAmount(), snapshot.amountDue(), snapshot.benefitId(), snapshot.rateId(),
				PaymentStatus.PENDING);
		paymentRepository.save(payment);
	}

	private Payment reload(Payment payment) {
		return paymentRepository.findById(payment.getId()).orElse(payment);
	}

	private static SettledDebt toResult(Payment payment) {
		BigDecimal original = payment.getOriginalAmount();
		BigDecimal due = payment.getAmountDue();
		return new SettledDebt(payment.getId(), original,
				original == null || due == null ? null : original.subtract(due), due, payment.getAmountPaid(),
				payment.getBenefitId(), payment.getStatus() == PaymentStatus.EXEMPT);
	}

	private static void validate(SettleDebtCommand command) {
		if (command.paymentId() == null) {
			throw new InvalidPaymentDataException("paymentId is required");
		}
		if (command.method() == null || command.channel() == null) {
			throw new InvalidPaymentDataException("method and channel are required");
		}
		ChargedSnapshot snapshot = command.snapshot();
		if (snapshot != null && (snapshot.originalAmount() == null || snapshot.amountDue() == null)) {
			throw new InvalidPaymentDataException("snapshot needs originalAmount and amountDue");
		}
	}
}
