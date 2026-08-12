package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.ListPaymentConceptsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.ListPaymentConceptsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.PaymentConceptSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase.UpdatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link PaymentConceptController}, mirroring
 * {@code SubjectClassificationControllerTest}'s style.
 */
@WebMvcTest(PaymentConceptController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentConceptControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListPaymentConceptsUseCase listPaymentConceptsUseCase;

	@MockitoBean
	private CreatePaymentConceptUseCase createPaymentConceptUseCase;

	@MockitoBean
	private GetPaymentConceptUseCase getPaymentConceptUseCase;

	@MockitoBean
	private UpdatePaymentConceptUseCase updatePaymentConceptUseCase;

	@MockitoBean
	private ChangePaymentConceptStatusUseCase changePaymentConceptStatusUseCase;

	@MockitoBean
	private JwtService jwtService;

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
	void createPaymentConceptReturns201WithBody() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(createPaymentConceptUseCase.createPaymentConcept(any())).thenReturn(
				new PaymentConceptResult(conceptId, "Inscripcion", "Descripcion", "Politicas",
						PaymentConceptType.ENROLLMENT, true, false, 1, 2, true, LocalDate.of(2026, 1, 1),
						LocalDate.of(2026, 12, 31), PaymentConceptStatus.ACTIVE));

		mockMvc.perform(post("/payment-concepts").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(conceptId.toString()))
				.andExpect(jsonPath("$.name").value("Inscripcion"))
				.andExpect(jsonPath("$.type").value("ENROLLMENT"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createPaymentConceptWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/payment-concepts").contentType("application/json")
				.content(objectMapper.writeValueAsString(bodyWithName(""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPaymentConceptWithMissingTypeReturns400() throws Exception {
		mockMvc.perform(post("/payment-concepts").contentType("application/json")
				.content("{\"name\":\"Inscripcion\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPaymentConceptWithInvalidRangeReturns400() throws Exception {
		when(createPaymentConceptUseCase.createPaymentConcept(any()))
				.thenThrow(new InvalidPaymentConceptDataException("maxPerStudent must be greater than 0: 0"));

		mockMvc.perform(post("/payment-concepts").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listPaymentConceptsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID conceptId = UUID.randomUUID();
		PaymentConceptSummary summary = new PaymentConceptSummary(conceptId, "Inscripcion",
				PaymentConceptType.ENROLLMENT, true, false, PaymentConceptStatus.ACTIVE);
		when(listPaymentConceptsUseCase.listPaymentConcepts(
				new ListPaymentConceptsQuery(PaymentConceptStatus.ACTIVE, "insc", 0, 20)))
				.thenReturn(new ListPaymentConceptsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/payment-concepts").param("status", "ACTIVE").param("search", "insc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(conceptId.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Inscripcion"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listPaymentConceptsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listPaymentConceptsUseCase.listPaymentConcepts(new ListPaymentConceptsQuery(null, null, 0, 20)))
				.thenReturn(new ListPaymentConceptsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/payment-concepts")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(listPaymentConceptsUseCase).listPaymentConcepts(new ListPaymentConceptsQuery(null, null, 0, 20));
	}

	@Test
	void listPaymentConceptsWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/payment-concepts").param("status", "NOT_A_STATUS"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getPaymentConceptReturns200WithBodyWhenFound() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(getPaymentConceptUseCase.getById(conceptId)).thenReturn(
				new PaymentConceptResult(conceptId, "Inscripcion", "Descripcion", "Politicas",
						PaymentConceptType.ENROLLMENT, true, false, 1, 2, true, LocalDate.of(2026, 1, 1),
						LocalDate.of(2026, 12, 31), PaymentConceptStatus.ACTIVE));

		mockMvc.perform(get("/payment-concepts/{id}", conceptId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(conceptId.toString()))
				.andExpect(jsonPath("$.name").value("Inscripcion"));
	}

	@Test
	void getPaymentConceptReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getPaymentConceptUseCase.getById(unknownId))
				.thenThrow(new PaymentConceptNotFoundException("Payment concept not found: " + unknownId));

		mockMvc.perform(get("/payment-concepts/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updatePaymentConceptReturns200WithBody() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(updatePaymentConceptUseCase.updatePaymentConcept(any())).thenReturn(
				new PaymentConceptResult(conceptId, "Reinscripcion", "Descripcion", "Politicas",
						PaymentConceptType.REINSCRIPTION, false, true, 3, 4, false, LocalDate.of(2027, 1, 1),
						LocalDate.of(2027, 6, 30), PaymentConceptStatus.ACTIVE));

		mockMvc.perform(put("/payment-concepts/{id}", conceptId).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Reinscripcion"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(conceptId.toString()))
				.andExpect(jsonPath("$.name").value("Reinscripcion"));
	}

	@Test
	void updatePaymentConceptWithBlankNameReturns400() throws Exception {
		mockMvc.perform(put("/payment-concepts/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(bodyWithName(""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updatePaymentConceptReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(updatePaymentConceptUseCase.updatePaymentConcept(any()))
				.thenThrow(new PaymentConceptNotFoundException("Payment concept not found: " + unknownId));

		mockMvc.perform(put("/payment-concepts/{id}", unknownId).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updatePaymentConceptWithInvalidRangeReturns400() throws Exception {
		when(updatePaymentConceptUseCase.updatePaymentConcept(any())).thenThrow(
				new InvalidPaymentConceptDataException("availableFrom must not be after availableUntil"));

		mockMvc.perform(put("/payment-concepts/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(changePaymentConceptStatusUseCase.changeStatus(
				new ChangeStatusCommand(callerId, conceptId, PaymentConceptStatus.INACTIVE))).thenReturn(
				new PaymentConceptResult(conceptId, "Inscripcion", "Descripcion", "Politicas",
						PaymentConceptType.ENROLLMENT, true, false, 1, 2, true, LocalDate.of(2026, 1, 1),
						LocalDate.of(2026, 12, 31), PaymentConceptStatus.INACTIVE));

		mockMvc.perform(patch("/payment-concepts/" + conceptId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(changePaymentConceptStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, conceptId, PaymentConceptStatus.INACTIVE));
	}

	@Test
	void changeStatusOfUnknownConceptReturns404() throws Exception {
		UUID conceptId = UUID.randomUUID();
		when(changePaymentConceptStatusUseCase.changeStatus(any()))
				.thenThrow(new PaymentConceptNotFoundException("Payment concept not found: " + conceptId));

		mockMvc.perform(patch("/payment-concepts/" + conceptId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.ACTIVE))))
				.andExpect(status().isNotFound());
	}

	private static CreateConceptBody validBody(String name) {
		return new CreateConceptBody(name, "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true, false, 1,
				2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}

	private static CreateConceptBody bodyWithName(String name) {
		return validBody(name);
	}

	private record CreateConceptBody(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
	}

	private record ChangeStatusBody(PaymentConceptStatus status) {
	}
}
