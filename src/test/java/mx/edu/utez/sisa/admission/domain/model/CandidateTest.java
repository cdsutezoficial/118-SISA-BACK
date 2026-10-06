package mx.edu.utez.sisa.admission.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateTest {

	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static Candidate candidateRegisteredAt(Instant registeredAt) {
		Candidate candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "registeredAt", registeredAt);
		return candidate;
	}

	@Test
	void paymentDeadline_countsTheRegistrationDayAsDayZero() {
		Candidate candidate = candidateRegisteredAt(Instant.parse("2026-09-01T18:00:00Z"));

		assertThat(candidate.registeredOn(ZONE)).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(candidate.paymentDeadline(ZONE, 10)).isEqualTo(LocalDate.of(2026, 9, 11));
	}

	@Test
	void registeredOn_readsTheDateInTheAdmissionZone_notUtc() {
		// 04:30 UTC on the 2nd is still 22:30 on the 1st in Mexico City, so the
		// window opens on the 1st — a UTC-based conversion would open it a day late.
		Candidate candidate = candidateRegisteredAt(Instant.parse("2026-09-02T04:30:00Z"));

		assertThat(candidate.registeredOn(ZONE)).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(candidate.paymentDeadline(ZONE, 10)).isEqualTo(LocalDate.of(2026, 9, 11));
	}

	@Test
	void markPaymentExpired_movesRegisteredToExpiredOnlyOnce() {
		Candidate candidate = candidateRegisteredAt(Instant.parse("2026-09-01T18:00:00Z"));

		assertThat(candidate.markPaymentExpired()).isTrue();
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
		assertThat(candidate.markPaymentExpired()).isFalse();
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
	}

	@Test
	void markPaymentExpired_neverTouchesAPaidFicha() {
		Candidate candidate = candidateRegisteredAt(Instant.parse("2026-09-01T18:00:00Z"));
		candidate.markPaid();

		assertThat(candidate.markPaymentExpired()).isFalse();
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PAID);
	}
}
