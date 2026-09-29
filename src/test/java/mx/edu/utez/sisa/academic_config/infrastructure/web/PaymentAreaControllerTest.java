package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentAreaStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentAreaStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.ListPaymentAreasQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.ListPaymentAreasResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.PaymentAreaSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentAreaUseCase.UpdatePaymentAreaCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentAreaJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentAreaJpaRepository.PaymentAreaOptionProjection;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentAreaNotFoundException;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link PaymentAreaController}, mirroring
 * {@code PaymentConceptControllerTest}'s style.
 */
@WebMvcTest(PaymentAreaController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentAreaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListPaymentAreasUseCase listPaymentAreasUseCase;

	@MockitoBean
	private CreatePaymentAreaUseCase createPaymentAreaUseCase;

	@MockitoBean
	private GetPaymentAreaUseCase getPaymentAreaUseCase;

	@MockitoBean
	private UpdatePaymentAreaUseCase updatePaymentAreaUseCase;

	@MockitoBean
	private ChangePaymentAreaStatusUseCase changePaymentAreaStatusUseCase;

	@MockitoBean
	private PaymentAreaJpaRepository paymentAreaJpaRepository;

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
	void createPaymentAreaReturns201WithBody() throws Exception {
		UUID areaId = UUID.randomUUID();
		when(createPaymentAreaUseCase.createPaymentArea(any())).thenReturn(
				new PaymentAreaResult(areaId, "Colegiaturas", "COL", "Descripcion", PaymentAreaStatus.ACTIVE));

		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Colegiaturas", "COL"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(areaId.toString()))
				.andExpect(jsonPath("$.name").value("Colegiaturas")).andExpect(jsonPath("$.code").value("COL"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createPaymentAreaWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("", "COL"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPaymentAreaWithBlankCodeReturns400() throws Exception {
		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Colegiaturas", ""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPaymentAreaWithDuplicateNameReturns409() throws Exception {
		when(createPaymentAreaUseCase.createPaymentArea(any()))
				.thenThrow(new DuplicatePaymentAreaNameException("duplicate"));

		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Colegiaturas", "COL"))))
				.andExpect(status().isConflict());
	}

	@Test
	void createPaymentAreaWithDuplicateCodeReturns409() throws Exception {
		when(createPaymentAreaUseCase.createPaymentArea(any()))
				.thenThrow(new DuplicatePaymentAreaCodeException("duplicate"));

		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Colegiaturas", "COL"))))
				.andExpect(status().isConflict());
	}

	@Test
	void listPaymentAreasReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID areaId = UUID.randomUUID();
		PaymentAreaSummary summary = new PaymentAreaSummary(areaId, "Colegiaturas", "COL", "Descripcion",
				PaymentAreaStatus.ACTIVE);
		when(listPaymentAreasUseCase.listPaymentAreas(new ListPaymentAreasQuery(PaymentAreaStatus.ACTIVE, "col", 0, 20)))
				.thenReturn(new ListPaymentAreasResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/payment-areas").param("status", "ACTIVE").param("search", "col"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(areaId.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Colegiaturas"))
				.andExpect(jsonPath("$.items[0].description").value("Descripcion"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listPaymentAreasDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listPaymentAreasUseCase.listPaymentAreas(new ListPaymentAreasQuery(null, null, 0, 20)))
				.thenReturn(new ListPaymentAreasResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/payment-areas")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listPaymentAreasUseCase).listPaymentAreas(new ListPaymentAreasQuery(null, null, 0, 20));
	}

	@Test
	void listPaymentAreasWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/payment-areas").param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());
	}

	@Test
	void listPaymentAreaOptionsReturnsActiveOptions() throws Exception {
		UUID areaId = UUID.randomUUID();
		PaymentAreaOptionProjection projection = mock(PaymentAreaOptionProjection.class);
		when(projection.getId()).thenReturn(areaId);
		when(projection.getName()).thenReturn("Colegiaturas");
		when(projection.getCode()).thenReturn("COL");
		when(paymentAreaJpaRepository.findByStatusOrderByNameAsc(PaymentAreaStatus.ACTIVE))
				.thenReturn(List.of(projection));

		mockMvc.perform(get("/payment-areas/options")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(areaId.toString()))
				.andExpect(jsonPath("$[0].label").value("Colegiaturas"))
				.andExpect(jsonPath("$[0].code").value("COL"));
	}

	@Test
	void getPaymentAreaReturns200WithBodyWhenFound() throws Exception {
		UUID areaId = UUID.randomUUID();
		when(getPaymentAreaUseCase.getById(areaId)).thenReturn(
				new PaymentAreaResult(areaId, "Colegiaturas", "COL", "Descripcion", PaymentAreaStatus.ACTIVE));

		mockMvc.perform(get("/payment-areas/{id}", areaId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(areaId.toString()))
				.andExpect(jsonPath("$.name").value("Colegiaturas"));
	}

	@Test
	void getPaymentAreaReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getPaymentAreaUseCase.getById(unknownId))
				.thenThrow(new PaymentAreaNotFoundException("Payment area not found: " + unknownId));

		mockMvc.perform(get("/payment-areas/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updatePaymentAreaReturns200WithBody() throws Exception {
		UUID areaId = UUID.randomUUID();
		when(updatePaymentAreaUseCase.updatePaymentArea(any())).thenReturn(
				new PaymentAreaResult(areaId, "Inscripcion", "INS", "Descripcion", PaymentAreaStatus.ACTIVE));

		mockMvc.perform(put("/payment-areas/{id}", areaId).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion", "INS"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(areaId.toString()))
				.andExpect(jsonPath("$.name").value("Inscripcion"));
	}

	@Test
	void updatePaymentAreaWithBlankNameReturns400() throws Exception {
		mockMvc.perform(put("/payment-areas/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("", "INS"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updatePaymentAreaReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(updatePaymentAreaUseCase.updatePaymentArea(any()))
				.thenThrow(new PaymentAreaNotFoundException("Payment area not found: " + unknownId));

		mockMvc.perform(put("/payment-areas/{id}", unknownId).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Colegiaturas", "COL"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updatePaymentAreaWithDuplicateCodeReturns409() throws Exception {
		when(updatePaymentAreaUseCase.updatePaymentArea(any()))
				.thenThrow(new DuplicatePaymentAreaCodeException("duplicate"));

		mockMvc.perform(put("/payment-areas/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Colegiaturas", "COL"))))
				.andExpect(status().isConflict());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID areaId = UUID.randomUUID();
		when(changePaymentAreaStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, areaId, PaymentAreaStatus.INACTIVE))).thenReturn(
						new PaymentAreaResult(areaId, "Colegiaturas", "COL", "Descripcion",
								PaymentAreaStatus.INACTIVE));

		mockMvc.perform(patch("/payment-areas/" + areaId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(changePaymentAreaStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, areaId, PaymentAreaStatus.INACTIVE));
	}

	@Test
	void changeStatusOfUnknownAreaReturns404() throws Exception {
		UUID areaId = UUID.randomUUID();
		when(changePaymentAreaStatusUseCase.changeStatus(any()))
				.thenThrow(new PaymentAreaNotFoundException("Payment area not found: " + areaId));

		mockMvc.perform(patch("/payment-areas/" + areaId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.ACTIVE))))
				.andExpect(status().isNotFound());
	}

	private static CreateAreaBody validBody(String name, String code) {
		return new CreateAreaBody(name, code, "Descripcion");
	}

	private record CreateAreaBody(String name, String code, String description) {
	}

	private record ChangeStatusBody(PaymentAreaStatus status) {
	}
}
