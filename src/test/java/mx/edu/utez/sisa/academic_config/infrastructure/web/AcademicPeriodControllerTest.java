package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.AdvanceAcademicPeriodStatusByDateUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.CreatePeriodCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.ListAcademicPeriodsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.ListAcademicPeriodsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.PeriodSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase.UpdatePeriodCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPeriodJpaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePeriodException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPeriodStatusTransitionException;
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
 * Thin-controller tests for {@link AcademicPeriodController}, mirroring
 * {@code SubjectClassificationControllerTest}'s style.
 */
@WebMvcTest(AcademicPeriodController.class)
@AutoConfigureMockMvc(addFilters = false)
class AcademicPeriodControllerTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListAcademicPeriodsUseCase listAcademicPeriodsUseCase;

	@MockitoBean
	private CreateAcademicPeriodUseCase createAcademicPeriodUseCase;

	@MockitoBean
	private GetAcademicPeriodUseCase getAcademicPeriodUseCase;

	@MockitoBean
	private UpdateAcademicPeriodUseCase updateAcademicPeriodUseCase;

	@MockitoBean
	private ChangeAcademicPeriodStatusUseCase changeAcademicPeriodStatusUseCase;

	@MockitoBean
	private AdvanceAcademicPeriodStatusByDateUseCase advanceAcademicPeriodStatusByDateUseCase;

	@MockitoBean
	private AcademicPeriodJpaRepository academicPeriodJpaRepository;

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
	void createPeriodReturns201WithBody() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(createAcademicPeriodUseCase.createPeriod(new CreatePeriodCommand("Enero-Abril 2026", 2026, 1,
				PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END)))
				.thenReturn(new PeriodResult(periodId, "Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START,
						END, ENROLLMENT_START, ENROLLMENT_END, PeriodStatus.CONFIGURATION));

		mockMvc.perform(post("/periods").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePeriodBody("Enero-Abril 2026", 2026, 1,
						PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(periodId.toString()))
				.andExpect(jsonPath("$.name").value("Enero-Abril 2026")).andExpect(jsonPath("$.year").value(2026))
				.andExpect(jsonPath("$.periodNumber").value(1))
				.andExpect(jsonPath("$.status").value("CONFIGURATION"));
	}

	@Test
	void createPeriodWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/periods").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePeriodBody("", 2026, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPeriodWithMissingTypeReturns400() throws Exception {
		mockMvc.perform(post("/periods").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePeriodBody("X", 2026, 1, null, START, END,
						ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPeriodWithDuplicateYearAndPeriodNumberReturns409() throws Exception {
		when(createAcademicPeriodUseCase.createPeriod(any()))
				.thenThrow(new DuplicatePeriodException("A period already exists for year 2026 and periodNumber 1"));

		mockMvc.perform(post("/periods").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePeriodBody("X", 2026, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isConflict());
	}

	@Test
	void listPeriodsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID periodId = UUID.randomUUID();
		PeriodSummary summary = new PeriodSummary(periodId, "Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL,
				START, END, ENROLLMENT_START, ENROLLMENT_END, PeriodStatus.CONFIGURATION);
		when(listAcademicPeriodsUseCase.listPeriods(
				new ListAcademicPeriodsQuery(PeriodStatus.CONFIGURATION, "enero", 0, 20)))
				.thenReturn(new ListAcademicPeriodsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/periods").param("status", "CONFIGURATION").param("search", "enero"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(periodId.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Enero-Abril 2026"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listPeriodsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listAcademicPeriodsUseCase.listPeriods(new ListAcademicPeriodsQuery(null, null, 0, 20)))
				.thenReturn(new ListAcademicPeriodsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/periods")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listAcademicPeriodsUseCase).listPeriods(new ListAcademicPeriodsQuery(null, null, 0, 20));
	}

	@Test
	void getPeriodReturns200WithBodyWhenFound() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(getAcademicPeriodUseCase.getById(periodId)).thenReturn(new PeriodResult(periodId, "Enero-Abril 2026",
				2026, 1, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END,
				PeriodStatus.CONFIGURATION));

		mockMvc.perform(get("/periods/{id}", periodId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(periodId.toString()));
	}

	@Test
	void getPeriodReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getAcademicPeriodUseCase.getById(unknownId))
				.thenThrow(new AcademicPeriodNotFoundException("Academic period not found: " + unknownId));

		mockMvc.perform(get("/periods/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updatePeriodReturns200WithBody() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(updateAcademicPeriodUseCase.updatePeriod(new UpdatePeriodCommand(periodId, "Renombrado", 2026, 2,
				PeriodType.SEMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END)))
				.thenReturn(new PeriodResult(periodId, "Renombrado", 2026, 2, PeriodType.SEMESTRAL, START, END,
						ENROLLMENT_START, ENROLLMENT_END, PeriodStatus.CONFIGURATION));

		mockMvc.perform(put("/periods/{id}", periodId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdatePeriodBody("Renombrado", 2026, 2,
						PeriodType.SEMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renombrado"))
				.andExpect(jsonPath("$.periodNumber").value(2));
	}

	@Test
	void updatePeriodReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(updateAcademicPeriodUseCase.updatePeriod(any()))
				.thenThrow(new AcademicPeriodNotFoundException("Academic period not found: " + unknownId));

		mockMvc.perform(put("/periods/{id}", unknownId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdatePeriodBody("X", 2026, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updatePeriodWithDuplicateYearAndPeriodNumberReturns409() throws Exception {
		when(updateAcademicPeriodUseCase.updatePeriod(any()))
				.thenThrow(new DuplicatePeriodException("A period already exists for year 2026 and periodNumber 2"));

		mockMvc.perform(put("/periods/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdatePeriodBody("X", 2026, 2, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isConflict());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(changeAcademicPeriodStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, periodId, PeriodStatus.ENROLLMENT)))
				.thenReturn(new PeriodResult(periodId, "Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START,
						END, ENROLLMENT_START, ENROLLMENT_END, PeriodStatus.ENROLLMENT));

		mockMvc.perform(patch("/periods/" + periodId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENROLLMENT"));

		verify(changeAcademicPeriodStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, periodId, PeriodStatus.ENROLLMENT));
	}

	@Test
	void changeStatusOfUnknownPeriodReturns404() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(changeAcademicPeriodStatusUseCase.changeStatus(any()))
				.thenThrow(new AcademicPeriodNotFoundException("Academic period not found: " + periodId));

		mockMvc.perform(patch("/periods/" + periodId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isNotFound());
	}

	@Test
	void changeStatusWithInvalidTransitionReturns400() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(changeAcademicPeriodStatusUseCase.changeStatus(any())).thenThrow(
				new InvalidPeriodStatusTransitionException("Invalid status transition from CONFIGURATION to ACTIVE"));

		mockMvc.perform(patch("/periods/" + periodId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ACTIVE))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void advanceByDateReturns200WithAdvancedCount() throws Exception {
		when(advanceAcademicPeriodStatusByDateUseCase.advanceAll(LocalDate.now())).thenReturn(2);

		mockMvc.perform(post("/periods/advance-by-date")).andExpect(status().isOk())
				.andExpect(jsonPath("$.advanced").value(2));
	}

	@Test
	void listPeriodOptionsReturnsOnlyActivePeriodsWithMinimalProjection() throws Exception {
		UUID periodId = UUID.randomUUID();
		AcademicPeriodJpaRepository.PeriodOptionProjection active = mock(
				AcademicPeriodJpaRepository.PeriodOptionProjection.class);
		when(active.getId()).thenReturn(periodId);
		when(active.getName()).thenReturn("Enero-Abril 2026");
		when(active.getYear()).thenReturn(2026);
		when(academicPeriodJpaRepository.findByStatusOrderByYearDescNameAsc(PeriodStatus.ACTIVE))
				.thenReturn(List.of(active));

		mockMvc.perform(get("/periods/options")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(periodId.toString()))
				.andExpect(jsonPath("$[0].label").value("Enero-Abril 2026"))
				.andExpect(jsonPath("$[0].code").value("2026"))
				.andExpect(jsonPath("$[1]").doesNotExist());

		verify(academicPeriodJpaRepository).findByStatusOrderByYearDescNameAsc(PeriodStatus.ACTIVE);
	}

	private record CreatePeriodBody(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
	}

	private record UpdatePeriodBody(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
	}

	private record ChangeStatusBody(PeriodStatus status) {
	}
}
