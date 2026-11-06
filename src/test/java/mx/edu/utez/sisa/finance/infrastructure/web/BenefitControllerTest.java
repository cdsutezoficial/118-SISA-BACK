package mx.edu.utez.sisa.finance.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.CreditStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CancelBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetDiscountReportUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetDiscountReportUseCase.DiscountReport;
import mx.edu.utez.sisa.finance.domain.port.in.ListBenefitsUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.UpdateBenefitUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link BenefitController}, mirroring
 * {@code FinanceControllerTest}'s style.
 */
@WebMvcTest(BenefitController.class)
@AutoConfigureMockMvc(addFilters = false)
class BenefitControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreateBenefitUseCase createBenefitUseCase;

	@MockitoBean
	private UpdateBenefitUseCase updateBenefitUseCase;

	@MockitoBean
	private CancelBenefitUseCase cancelBenefitUseCase;

	@MockitoBean
	private ListBenefitsUseCase listBenefitsUseCase;

	@MockitoBean
	private GetDiscountReportUseCase getDiscountReportUseCase;

	@MockitoBean
	private PaymentCreditRepository paymentCreditRepository;

	@MockitoBean
	private QuoteDebtUseCase quoteDebtUseCase;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	private UUID callerId;

	@BeforeEach
	void setUp() {
		callerId = UUID.randomUUID();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				callerId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createBenefitReturns201WithBody() throws Exception {
		UUID candidateId = UUID.randomUUID();
		when(createBenefitUseCase.createBenefit(any())).thenReturn(
				view(UUID.randomUUID(), candidateId, null, BenefitStatus.ACTIVE, true));

		mockMvc.perform(post("/finance/benefits").contentType("application/json")
				.content(objectMapper.writeValueAsString(createBody(candidateId))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.candidateId").value(candidateId.toString()))
				.andExpect(jsonPath("$.mode").value("PERCENT")).andExpect(jsonPath("$.percent").value(50.00))
				.andExpect(jsonPath("$.inForce").value(true));
	}

	@Test
	void createBenefitWithoutPersonReturns400() throws Exception {
		mockMvc.perform(post("/finance/benefits").contentType("application/json")
				.content("{\"conceptCode\":\"TUITION\",\"mode\":\"FULL\",\"validFrom\":\"2026-10-01\","
						+ "\"validTo\":\"2026-12-31\",\"reason\":\"beca\",\"grantedByName\":\"Finanzas\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listBenefitsReturns200WithItems() throws Exception {
		when(listBenefitsUseCase.listBenefits(any())).thenReturn(
				List.of(view(UUID.randomUUID(), UUID.randomUUID(), null, BenefitStatus.ACTIVE, true)));

		mockMvc.perform(get("/finance/benefits")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].mode").value("PERCENT"))
				.andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void cancelBenefitReturns200() throws Exception {
		UUID id = UUID.randomUUID();
		when(cancelBenefitUseCase.cancelBenefit(id))
				.thenReturn(view(id, UUID.randomUUID(), null, BenefitStatus.CANCELLED, false));

		mockMvc.perform(patch("/finance/benefits/{id}/cancel", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void listCreditsReturns200() throws Exception {
		UUID candidateId = UUID.randomUUID();
		when(paymentCreditRepository.findByCandidateId(candidateId))
				.thenReturn(List.of(new PaymentCredit(candidateId, null, UUID.randomUUID(), UUID.randomUUID(),
						new BigDecimal("150.00"), Instant.parse("2026-10-19T00:00:00Z"))));

		mockMvc.perform(get("/finance/credits").param("candidateId", candidateId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].amount").value(150.00))
				.andExpect(jsonPath("$.items[0].status").value(CreditStatus.AVAILABLE.name()));
	}

	@Test
	void reportReturns200() throws Exception {
		when(getDiscountReportUseCase.getReport(any(), any()))
				.thenReturn(new DiscountReport(null, null, 1,
						List.of(view(UUID.randomUUID(), UUID.randomUUID(), null, BenefitStatus.ACTIVE, true))));

		mockMvc.perform(get("/finance/discounts/report")).andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
	}

	private static BenefitView view(UUID id, UUID candidateId, UUID studentId, BenefitStatus status, boolean inForce) {
		return new BenefitView(id, candidateId, studentId, "TUITION", null, null, BenefitMode.PERCENT,
				new BigDecimal("50.00"), null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), status,
				"beca", UUID.randomUUID(), "Finanzas", Instant.parse("2026-10-19T00:00:00Z"), inForce, null);
	}

	private BenefitBody createBody(UUID candidateId) {
		return new BenefitBody(candidateId, "TUITION", BenefitMode.PERCENT, new BigDecimal("50.00"), null,
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "beca", "Finanzas");
	}

	private record BenefitBody(UUID candidateId, String conceptCode, BenefitMode mode, BigDecimal percent,
			BigDecimal amount, LocalDate validFrom, LocalDate validTo, String reason, String grantedByName) {
	}
}
