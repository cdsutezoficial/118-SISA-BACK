package mx.edu.utez.sisa.finance.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentResult;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentSnapshotStatus;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.AccountStatement;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.StatementPayment;
import mx.edu.utez.sisa.finance.domain.port.in.GetPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository.PaymentSearchPage;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link FinanceController}, mirroring
 * {@code PaymentAreaControllerTest}'s style.
 */
@WebMvcTest(FinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class FinanceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreatePaymentUseCase createPaymentUseCase;

	@MockitoBean
	private RegisterPaymentEntryUseCase registerPaymentEntryUseCase;

	@MockitoBean
	private GetPaymentUseCase getPaymentUseCase;

	@MockitoBean
	private GetAccountStatementUseCase getAccountStatementUseCase;

	@MockitoBean
	private RegisterExemptionUseCase registerExemptionUseCase;

	@MockitoBean
	private ApplyCreditToPaymentUseCase applyCreditToPaymentUseCase;

	@MockitoBean
	private PaymentRepository paymentRepository;

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
	void createPaymentReturns201WithBody() throws Exception {
		UUID paymentId = UUID.randomUUID();
		UUID candidateId = UUID.randomUUID();
		when(createPaymentUseCase.createPayment(any()))
				.thenReturn(new PaymentResult(paymentId, candidateId, null, "TUITION", new BigDecimal("1000.00"),
						new BigDecimal("750.00"), new BigDecimal("0.00"), PaymentSnapshotStatus.PENDING,
						PaymentOrigin.CASHIER, null, Instant.parse("2026-10-19T00:00:00Z"), null));

		mockMvc.perform(post("/finance/payments").contentType("application/json")
				.content(objectMapper.writeValueAsString(createBody(candidateId))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(paymentId.toString()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.amountDue").value(750.00));
	}

	@Test
	void createPaymentWithoutPersonReturns400() throws Exception {
		mockMvc.perform(post("/finance/payments").contentType("application/json")
				.content("{\"conceptCode\":\"TUITION\",\"programId\":\"" + UUID.randomUUID()
						+ "\",\"origin\":\"CASHIER\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void registerEntryReturns200WithBody() throws Exception {
		UUID paymentId = UUID.randomUUID();
		UUID candidateId = UUID.randomUUID();
		when(registerPaymentEntryUseCase.registerEntry(any()))
				.thenReturn(new PaymentResult(paymentId, candidateId, null, "TUITION", new BigDecimal("1000.00"),
						new BigDecimal("1000.00"), new BigDecimal("1000.00"), PaymentSnapshotStatus.PAID,
						PaymentOrigin.CASHIER, null, Instant.parse("2026-10-19T00:00:00Z"), null));

		mockMvc.perform(post("/finance/payments/{id}/entries", paymentId).contentType("application/json")
				.content("{\"amount\":1000.00,\"method\":\"CASH\",\"channel\":\"CASHIER\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.amountPaid").value(1000.00));
	}

	@Test
	void listPaymentsReturns200WithPaginationMetadata() throws Exception {
		Payment payment = payment(UUID.randomUUID(), PaymentStatus.PENDING);
		when(paymentRepository.search(any())).thenReturn(new PaymentSearchPage(List.of(payment), 1L, 1));

		mockMvc.perform(get("/finance/payments")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].conceptCode").value("TUITION"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void getPaymentReturns200WithDetail() throws Exception {
		UUID paymentId = UUID.randomUUID();
		when(getPaymentUseCase.getPayment(paymentId)).thenReturn(statementPayment(paymentId, PaymentStatus.PARTIAL));

		mockMvc.perform(get("/finance/payments/{id}", paymentId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId.toString()))
				.andExpect(jsonPath("$.balance").value(700.00));
	}

	@Test
	void getPaymentNotFoundReturns404() throws Exception {
		UUID paymentId = UUID.randomUUID();
		when(getPaymentUseCase.getPayment(paymentId))
				.thenThrow(new PaymentNotFoundException("Payment not found: " + paymentId));

		mockMvc.perform(get("/finance/payments/{id}", paymentId)).andExpect(status().isNotFound());
	}

	@Test
	void accountStatementReturns200() throws Exception {
		UUID candidateId = UUID.randomUUID();
		when(getAccountStatementUseCase.getAccountStatement(candidateId, null)).thenReturn(new AccountStatement(
				candidateId, null, "Ana Lopez", List.of(statementPayment(UUID.randomUUID(), PaymentStatus.PAID))));

		mockMvc.perform(get("/finance/account-statement").param("candidateId", candidateId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.personName").value("Ana Lopez"))
				.andExpect(jsonPath("$.payments[0].status").value("PAID"));
	}

	private CreatePaymentBody createBody(UUID candidateId) {
		return new CreatePaymentBody(candidateId, "TUITION", UUID.randomUUID(), PaymentOrigin.CASHIER);
	}

	private static Payment payment(UUID candidateId, PaymentStatus status) {
		return new Payment(candidateId, null, "TUITION", null, null, UUID.randomUUID(), new BigDecimal("1000.00"),
				new BigDecimal("1000.00"), null, status, PaymentOrigin.CASHIER, null,
				Instant.parse("2026-10-19T00:00:00Z"));
	}

	private static StatementPayment statementPayment(UUID paymentId, PaymentStatus status) {
		return new StatementPayment(paymentId, "TUITION", new BigDecimal("1000.00"), new BigDecimal("1000.00"),
				new BigDecimal("300.00"), new BigDecimal("700.00"), status, PaymentOrigin.CASHIER, null,
				Instant.parse("2026-10-19T00:00:00Z"), null, List.of());
	}

	private record CreatePaymentBody(UUID candidateId, String conceptCode, UUID programId, PaymentOrigin origin) {
	}
}
