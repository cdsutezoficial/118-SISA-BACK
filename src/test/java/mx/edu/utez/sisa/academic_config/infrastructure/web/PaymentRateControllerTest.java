package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link PaymentRateController}, mirroring
 * {@code PaymentConceptControllerTest}'s style.
 */
@WebMvcTest(PaymentRateController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentRateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private SetPaymentRateUseCase setPaymentRateUseCase;

	@MockitoBean
	private ListPaymentRatesUseCase listPaymentRatesUseCase;

	@MockitoBean
	private JwtService jwtService;

	@Test
	void setRateReturns201WithBody() throws Exception {
		UUID conceptId = UUID.randomUUID();
		UUID rateId = UUID.randomUUID();
		when(setPaymentRateUseCase.setRate(any())).thenReturn(new PaymentRateResult(rateId, conceptId, null, null,
				BigDecimal.valueOf(1500), null, LocalDate.of(2026, 1, 1), null));

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", conceptId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(null, null, BigDecimal.valueOf(1500), null,
						LocalDate.of(2026, 1, 1)))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(rateId.toString()))
				.andExpect(jsonPath("$.conceptId").value(conceptId.toString()))
				.andExpect(jsonPath("$.amount").value(1500))
				.andExpect(jsonPath("$.validTo").doesNotExist());
	}

	@Test
	void setRateWithMissingAmountReturns400() throws Exception {
		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content("{\"validFrom\":\"2026-01-01\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void setRateWithMissingValidFromReturns400() throws Exception {
		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content("{\"amount\":1500}")).andExpect(status().isBadRequest());
	}

	@Test
	void setRateWithNonExistentConceptReturns400() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(setPaymentRateUseCase.setRate(any()))
				.thenThrow(new PaymentConceptReferenceNotFoundException("Payment concept not found: " + conceptId));

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", conceptId).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, null, BigDecimal.valueOf(1500), null, LocalDate.of(2026, 1, 1)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void setRateWithZeroAmountReturns400() throws Exception {
		when(setPaymentRateUseCase.setRate(any()))
				.thenThrow(new InvalidPaymentRateDataException("amount must be greater than zero: 0"));

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, null, BigDecimal.ZERO, null, LocalDate.of(2026, 1, 1)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void setRateWithDuplicatePeriodScopedCombinationReturns409() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(setPaymentRateUseCase.setRate(any()))
				.thenThrow(new DuplicatePaymentRateException("A rate already exists for this combination"));

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, null, BigDecimal.valueOf(2000), periodId, LocalDate.of(2026, 1, 1)))))
				.andExpect(status().isConflict());
	}

	@Test
	void listRatesReturns200WithFlatArrayAndNoPaginationMetadata() throws Exception {
		UUID conceptId = UUID.randomUUID();
		UUID rateId = UUID.randomUUID();
		when(listPaymentRatesUseCase.listRates(conceptId)).thenReturn(List.of(new PaymentRateResult(rateId, conceptId,
				null, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(1500), null, LocalDate.of(2026, 1, 1), null)));

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", conceptId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(rateId.toString()))
				.andExpect(jsonPath("$.items[0].level").value("LICENCIATURA"))
				.andExpect(jsonPath("$.totalElements").doesNotExist());
	}

	@Test
	void listRatesReturns200WithEmptyArrayWhenNoHistoryExists() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(listPaymentRatesUseCase.listRates(conceptId)).thenReturn(List.of());

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", conceptId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
	}

	private record CreateBody(UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId,
			LocalDate validFrom) {
	}
}
