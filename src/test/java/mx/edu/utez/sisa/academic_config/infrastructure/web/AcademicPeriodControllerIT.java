package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPeriodJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /periods} security matchers
 * and business rules: real H2, real JWT filter chain, no mocks — mirroring
 * {@code SubjectClassificationControllerIT}'s style. Written from the start
 * (not added later) — the plan explicitly calls out not repeating the
 * {@code SubjectClassification} Phase-1 mistake of skipping the IT.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicPeriodControllerIT {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private AcademicPeriodJpaRepository jpaRepository;

	// --- List ---

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(newPeriod("Enero-Abril IT-ADM", 2101, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/periods").header("Authorization", "Bearer " + token).param("search", "IT-ADM"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void serviciosEscolaresCanList() throws Exception {
		jpaRepository.save(newPeriod("Enero-Abril IT-SE", 2102, 1));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/periods").header("Authorization", "Bearer " + token).param("search", "IT-SE"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/periods").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/periods")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	// --- Create ---

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/periods").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Enero-Abril 2103", 2103, 1,
						PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIGURATION"))
				.andExpect(jsonPath("$.year").value(2103));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/periods").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Enero-Abril 2104", 2104, 1,
						PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isCreated());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/periods").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Forbidden", 2105, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/periods").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Unauth", 2106, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void duplicateYearAndPeriodNumberReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/periods").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody("Primero", 2107, 1, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
								ENROLLMENT_END))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/periods").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody("Duplicado", 2107, 1, PeriodType.SEMESTRAL, START, END, ENROLLMENT_START,
								ENROLLMENT_END))))
				.andExpect(status().isConflict());
	}

	@Test
	void invalidDateRangeReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/periods").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody("Invalido", 2108, 1, PeriodType.CUATRIMESTRAL, END, START, ENROLLMENT_START,
								ENROLLMENT_END))))
				.andExpect(status().isBadRequest());
	}

	// --- Get by id ---

	@Test
	void adminCanGetById() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril GET-ADM", 2109, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/periods/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()));
	}

	@Test
	void otherRoleIsForbiddenOnGetById() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril GET-DOC", 2110, 1));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/periods/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril GET-UNA", 2111, 1));

		mockMvc.perform(get("/periods/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/periods/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	// --- Update ---

	@Test
	void adminCanUpdate() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril UPD-ADM", 2112, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/periods/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Renombrado", 2112, 2, PeriodType.SEMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renombrado"))
				.andExpect(jsonPath("$.periodNumber").value(2));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril UPD-DOC", 2113, 1));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/periods/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Forbidden", 2113, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUpdateReturns401() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril UPD-UNA", 2114, 1));

		mockMvc.perform(put("/periods/{id}", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Unauth", 2114, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/periods/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Fantasma", 2115, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateWithYearAndPeriodNumberCollidingWithAnotherRecordReturns409() throws Exception {
		jpaRepository.save(newPeriod("Primero", 2116, 1));
		AcademicPeriod target = jpaRepository.save(newPeriod("Segundo", 2116, 2));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/periods/{id}", target.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Colision", 2116, 1, PeriodType.CUATRIMESTRAL,
						START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isConflict());
	}

	@Test
	void updateWithUnchangedYearAndPeriodNumberOnOwnRecordSucceeds() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril SELF", 2117, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/periods/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Renombrado", 2117, 1,
						PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renombrado"));
	}

	// --- ChangeStatus ---

	@Test
	void adminCanChangeStatus() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril STA-ADM", 2118, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/periods/{id}/status", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENROLLMENT"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril STA-SE", 2119, 1));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/periods/{id}/status", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENROLLMENT"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril STA-DOC", 2120, 1));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/periods/{id}/status", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril STA-UNA", 2121, 1));

		mockMvc.perform(patch("/periods/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/periods/{id}/status", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ENROLLMENT))))
				.andExpect(status().isNotFound());
	}

	@Test
	void changeStatusWithInvalidTransitionReturns400() throws Exception {
		AcademicPeriod saved = jpaRepository.save(newPeriod("Enero-Abril STA-INV", 2122, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/periods/{id}/status", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PeriodStatus.ACTIVE))))
				.andExpect(status().isBadRequest());
	}

	private static AcademicPeriod newPeriod(String name, int year, int periodNumber) {
		return new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
	}

	private record UpdateBody(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
	}

	private record ChangeStatusBody(PeriodStatus status) {
	}
}
