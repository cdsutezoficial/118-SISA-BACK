package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;

class ReceiptNumbersTest {

	/** 23:30 in Mexico City is already the next day in UTC: the receipt keeps the local date. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T05:30:00Z"),
			ZoneId.of("America/Mexico_City"));

	private final FichaPaymentPort port = mock(FichaPaymentPort.class);

	private final ReceiptNumbers receipts = new ReceiptNumbers(port, CLOCK);

	@Test
	void usesTheAdmissionClockDate() {
		assertThat(receipts.money("ADM-2026-000123", false)).isEqualTo("REC-20261105-000123");
		assertThat(receipts.credit("ADM-2026-000123", true)).isEqualTo("SAF-20261105-000123-C");
		assertThat(receipts.exemption("ADM-2026-000123")).isEqualTo("EXE-20261105-000123");
	}

	/** Two differences of the same folio paid the same day do not share a receipt. */
	@Test
	void aNumberAlreadyUsedThatDayGetsASuffix() {
		when(port.receiptInUse("REC-20261105-000123-C")).thenReturn(true);
		when(port.receiptInUse("REC-20261105-000123-C-2")).thenReturn(true);

		assertThat(receipts.money("ADM-2026-000123", true)).isEqualTo("REC-20261105-000123-C-3");
	}
}
