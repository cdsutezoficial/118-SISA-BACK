package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateStaffDetailResponseTest {

	@Test
	void theFichaPaymentCarriesItsPriceBreakdown() {
		FichaData ficha = ficha(new BigDecimal("1000.00"), new BigDecimal("250.00"), new BigDecimal("750.00"),
				"Beca por promedio", "Ficha de admisión TSU");

		CandidateStaffDetailResponse.PaymentResponse payment = CandidateStaffDetailResponse.from(ficha)
				.admissionPayment();

		assertThat(payment.concept()).isEqualTo(AdmissionPaymentConcept.ADMISSION_FICHA);
		assertThat(payment.conceptName()).isEqualTo("Ficha de admisión TSU");
		assertThat(payment.originalAmount()).isEqualByComparingTo("1000.00");
		assertThat(payment.discountAmount()).isEqualByComparingTo("250.00");
		assertThat(payment.discountReason()).isEqualTo("Beca por promedio");
		assertThat(payment.amount()).isEqualByComparingTo("750.00");
		assertThat(payment.referenceNumber()).isEqualTo("REF-ADM-2026-000001");
	}

	@Test
	void aFichaWithoutDiscountLeavesTheDiscountEmpty() {
		FichaData ficha = ficha(new BigDecimal("1000.00"), null, new BigDecimal("1000.00"), null,
				"Ficha de admisión TSU");

		CandidateStaffDetailResponse.PaymentResponse payment = CandidateStaffDetailResponse.from(ficha)
				.admissionPayment();

		assertThat(payment.originalAmount()).isEqualByComparingTo("1000.00");
		assertThat(payment.discountAmount()).isNull();
		assertThat(payment.discountReason()).isNull();
		assertThat(payment.amount()).isEqualByComparingTo("1000.00");
	}

	private static FichaData ficha(BigDecimal originalAmount, BigDecimal discountAmount, BigDecimal amount,
			String discountReason, String conceptName) {
		return new FichaData(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", CandidateStatus.REGISTERED,
				Instant.parse("2026-10-20T16:00:00Z"), UUID.randomUUID(), "Desarrollo de Software", "DATIC",
				"PELJ000101HMSRRN09", "Juan", "Pérez", "López", "juan@example.com", null, "7771234567",
				"REF-ADM-2026-000001", amount, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 30),
				LocalDate.of(2026, 10, 30), AdmissionPaymentStatus.PENDING, null, null, null, null, null, null,
				null, null, null, null, originalAmount, discountAmount, discountReason, conceptName);
	}
}
