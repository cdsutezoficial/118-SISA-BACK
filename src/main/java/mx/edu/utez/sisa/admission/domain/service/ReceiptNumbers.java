package mx.edu.utez.sisa.admission.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;

/**
 * Official receipt numbers of a ficha: {@code PREFIX-yyyyMMdd-folioSeq[-C]}, where the
 * prefix says how it was paid ({@code REC} money, {@code SAF} saldo a favor, {@code EXE}
 * 100 % discount) and {@code -C} marks the difference of a career change.
 *
 * <p>The receipt is the official one (dominio/08-pagos), so it is never repeated. Two
 * operations of the same folio on the same day would produce the same base number (two
 * differences paid the same day, for instance); the second one gets {@code -2}, the next
 * {@code -3}, and so on. The date comes from the admission clock, like the rest of the flow.
 */
final class ReceiptNumbers {

	/** Far more than one folio can pay in a day; past it something is looping. */
	private static final int MAX_PER_DAY = 99;

	private final FichaPaymentPort fichaPaymentPort;

	private final Clock clock;

	ReceiptNumbers(FichaPaymentPort fichaPaymentPort, Clock clock) {
		this.fichaPaymentPort = fichaPaymentPort;
		this.clock = clock;
	}

	String money(String folio, boolean programChange) {
		return next("REC", folio, programChange);
	}

	String credit(String folio, boolean programChange) {
		return next("SAF", folio, programChange);
	}

	String exemption(String folio) {
		return next("EXE", folio, false);
	}

	private String next(String prefix, String folio, boolean programChange) {
		String seq = folio.substring(folio.lastIndexOf('-') + 1);
		String base = String.format("%s-%s-%s%s", prefix, LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE),
				seq, programChange ? "-C" : "");
		if (!fichaPaymentPort.receiptInUse(base)) {
			return base;
		}
		for (int n = 2; n <= MAX_PER_DAY; n++) {
			String candidate = base + "-" + n;
			if (!fichaPaymentPort.receiptInUse(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Se agotaron los recibos del día para " + base);
	}
}
