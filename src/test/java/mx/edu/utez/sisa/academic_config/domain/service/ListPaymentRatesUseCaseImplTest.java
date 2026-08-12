package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPaymentRatesUseCaseImplTest {

	@Mock
	private PaymentRateRepository paymentRateRepository;

	private ListPaymentRatesUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListPaymentRatesUseCaseImpl(paymentRateRepository);
	}

	@Test
	void listRates_returnsTheFullHistoryInRepositoryOrder() {
		UUID conceptId = UUID.randomUUID();
		PaymentRate older = new PaymentRate(conceptId, null, null, BigDecimal.valueOf(1000), null,
				LocalDate.of(2025, 1, 1));
		PaymentRate newer = new PaymentRate(conceptId, null, null, BigDecimal.valueOf(1500), null,
				LocalDate.of(2026, 1, 1));
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of(newer, older));

		List<PaymentRateResult> result = useCase.listRates(conceptId);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(result.get(1).amount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
	}

	@Test
	void listRates_returnsEmptyListWhenConceptHasNoRates() {
		UUID conceptId = UUID.randomUUID();
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of());

		List<PaymentRateResult> result = useCase.listRates(conceptId);

		assertThat(result).isEmpty();
	}

	@Test
	void listRates_mapsAllFieldsIncludingValidTo() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		PaymentRate rate = new PaymentRate(conceptId, programId, AcademicLevel.TSU, BigDecimal.valueOf(800), periodId,
				LocalDate.of(2026, 1, 1));
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of(rate));

		PaymentRateResult result = useCase.listRates(conceptId).get(0);

		assertThat(result.conceptId()).isEqualTo(conceptId);
		assertThat(result.programId()).isEqualTo(programId);
		assertThat(result.level()).isEqualTo(AcademicLevel.TSU);
		assertThat(result.periodId()).isEqualTo(periodId);
		assertThat(result.validTo()).isNull();
	}
}
