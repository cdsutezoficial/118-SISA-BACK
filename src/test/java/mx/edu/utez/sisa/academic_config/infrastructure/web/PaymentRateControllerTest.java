package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.ReconcilePaymentRatesCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ReconcilePaymentRatesRequest;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.IncompletePaymentRateSetException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link PaymentRateController}, mirroring
 * {@code PaymentConceptControllerTest}'s style.
 *
 * <p>The write side used to be a {@code POST} of one rate and had four separate
 * rejection paths to cover. It is now a single {@code PUT} of the complete set,
 * so most of what these tests check has moved to
 * {@code ReconcilePaymentRatesUseCaseImplTest}; what is left here is that the
 * controller hands the payload over untouched, answers {@code 200} with the set
 * as it stands afterwards, and that the three rejections worth naming at the
 * boundary keep their status codes.
 */
@WebMvcTest(PaymentRateController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentRateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ReconcilePaymentRatesUseCase reconcilePaymentRatesUseCase;

	@MockitoBean
	private ListPaymentRatesUseCase listPaymentRatesUseCase;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	@Test
	void reconcileReturns200WithTheSetAsItStandsAfterwards() throws Exception {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID rateId = UUID.randomUUID();
		when(reconcilePaymentRatesUseCase.reconcileRates(any())).thenReturn(List.of(new PaymentRateResult(rateId, conceptId,
				programId, null, BigDecimal.valueOf(1500), null, PaymentRateStatus.ACTIVE,
				LocalDateTime.of(2026, 1, 1, 8, 0))));

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", conceptId).contentType("application/json")
				.content(objectMapper.writeValueAsString(request(draft(programId, null, 1500)))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(rateId.toString()))
				.andExpect(jsonPath("$.items[0].conceptId").value(conceptId.toString()))
				.andExpect(jsonPath("$.items[0].programId").value(programId.toString()))
				.andExpect(jsonPath("$.items[0].amount").value(1500))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
	}

	/**
	 * The controller is a pass-through, so what it sends is what it received —
	 * including the path's concept id, which is not in the body and would
	 * otherwise be a value the client could disagree with.
	 */
	@Test
	void reconcileTakesTheConceptIdFromThePathAndForwardsEveryDestination() throws Exception {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		when(reconcilePaymentRatesUseCase.reconcileRates(any())).thenReturn(List.of());

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", conceptId).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						request(draft(programId, null, 1500), draft(null, AcademicLevel.LICENCIATURA, 900),
								draft(null, null, 500, periodId)))))
				.andExpect(status().isOk());

		ArgumentCaptor<ReconcilePaymentRatesCommand> command = ArgumentCaptor
				.forClass(ReconcilePaymentRatesCommand.class);
		verify(reconcilePaymentRatesUseCase).reconcileRates(command.capture());

		assertThat(command.getValue().conceptId()).isEqualTo(conceptId);
		assertThat(command.getValue().rates()).extracting(rate -> rate.programId()).containsExactly(programId, null, null);
		assertThat(command.getValue().rates()).extracting(rate -> rate.level()).containsExactly(null,
				AcademicLevel.LICENCIATURA, null);
		assertThat(command.getValue().rates()).extracting(rate -> rate.periodId()).containsExactly(null, null, periodId);
	}

	/**
	 * An absent {@code rates} key is not an error: for a concept that is not a
	 * periodic quota, an empty set is the instruction to withdraw every price.
	 * It is the use case, which knows the concept type, that may refuse.
	 */
	@Test
	void reconcileWithNoRatesReturns200AndAnEmptyArray() throws Exception {
		when(reconcilePaymentRatesUseCase.reconcileRates(any())).thenReturn(List.of());

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void reconcileWithNonExistentConceptReturns400() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(reconcilePaymentRatesUseCase.reconcileRates(any()))
				.thenThrow(new PaymentConceptReferenceNotFoundException("Payment concept not found: " + conceptId));

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", conceptId).contentType("application/json")
				.content(objectMapper.writeValueAsString(request(draft(UUID.randomUUID(), null, 1500)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reconcileWithZeroAmountReturns400() throws Exception {
		when(reconcilePaymentRatesUseCase.reconcileRates(any()))
				.thenThrow(new InvalidPaymentRateDataException("amount must be greater than zero: 0"));

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(request(draft(UUID.randomUUID(), null, 0)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void reconcileWithDuplicateDestinationReturns409() throws Exception {
		UUID programId = UUID.randomUUID();
		when(reconcilePaymentRatesUseCase.reconcileRates(any()))
				.thenThrow(new DuplicatePaymentRateException("Destination priced twice in the same payload"));

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper
						.writeValueAsString(request(draft(programId, null, 2000), draft(programId, null, 2100)))))
				.andExpect(status().isConflict());
	}

	@Test
	void reconcileWithAQuotaMissingACareerReturns409() throws Exception {
		when(reconcilePaymentRatesUseCase.reconcileRates(any()))
				.thenThrow(new IncompletePaymentRateSetException("Missing rates for: Ingenieria"));

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(request(draft(UUID.randomUUID(), null, 1500)))))
				.andExpect(status().isConflict());
	}

	@Test
	void listRatesReturns200WithFlatArrayAndNoPaginationMetadata() throws Exception {
		UUID conceptId = UUID.randomUUID();
		UUID rateId = UUID.randomUUID();
		when(listPaymentRatesUseCase.listRates(conceptId)).thenReturn(List.of(new PaymentRateResult(rateId, conceptId,
				null, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(1500), null, PaymentRateStatus.INACTIVE,
				LocalDateTime.of(2025, 3, 1, 8, 0))));

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", conceptId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(rateId.toString()))
				.andExpect(jsonPath("$.items[0].level").value("LICENCIATURA"))
				.andExpect(jsonPath("$.items[0].status").value("INACTIVE"))
				.andExpect(jsonPath("$.items[0].createdAt").exists())
				// the date range this response used to carry is gone
				.andExpect(jsonPath("$.items[0].validTo").doesNotExist())
				.andExpect(jsonPath("$.totalElements").doesNotExist());
	}

	@Test
	void listRatesReturns200WithEmptyArrayWhenNoHistoryExists() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(listPaymentRatesUseCase.listRates(conceptId)).thenReturn(List.of());

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", conceptId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
	}

	private static ReconcilePaymentRatesRequest request(ReconcilePaymentRatesRequest.PaymentRateDraftRequest... rates) {
		return new ReconcilePaymentRatesRequest(List.of(rates));
	}

	private static ReconcilePaymentRatesRequest.PaymentRateDraftRequest draft(UUID programId, AcademicLevel level,
			int amount) {
		return draft(programId, level, amount, null);
	}

	private static ReconcilePaymentRatesRequest.PaymentRateDraftRequest draft(UUID programId, AcademicLevel level,
			int amount, UUID periodId) {
		return new ReconcilePaymentRatesRequest.PaymentRateDraftRequest(programId, level, BigDecimal.valueOf(amount),
				periodId);
	}
}