package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentConceptJpaRepository;
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
 * End-to-end integration coverage for the {@code /payment-concepts} security
 * matchers: real H2, real JWT filter chain, no mocks — mirroring
 * {@code SubjectClassificationControllerIT}'s style. The critical difference
 * from every sibling IT in this module: {@code SERVICIOS_ESCOLARES} is
 * deliberately NOT granted on this endpoint (unlike every other
 * {@code academic_config} aggregate) — {@code PERSONAL_FINANZAS} is granted
 * instead, per plan section 6 and the security config's Javadoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentConceptControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PaymentConceptJpaRepository jpaRepository;

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(newConcept("Inscripcion ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-concepts").header("Authorization", "Bearer " + token)
				.param("search", "ADM")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void personalFinanzasCanList() throws Exception {
		jpaRepository.save(newConcept("Inscripcion FIN"));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(get("/payment-concepts").header("Authorization", "Bearer " + token)
				.param("search", "FIN")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnList() throws Exception {
		// Regression guard: unlike every other academic_config aggregate,
		// SERVICIOS_ESCOLARES is deliberately NOT granted on this endpoint —
		// this catalog is administered by Finanzas (plan section 6).
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-concepts").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/payment-concepts")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion Admin"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.name").value("Inscripcion Admin"));
	}

	@Test
	void personalFinanzasCanCreate() throws Exception {
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion Finanzas"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden Docente"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/payment-concepts").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Unauth"))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createAllowsDuplicateName() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Concepto Duplicado"))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Concepto Duplicado"))))
				.andExpect(status().isCreated());
	}

	@Test
	void createWithZeroMaxPerStudentReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(bodyWithMaxPerStudent(0))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithNegativeMaxPerPeriodReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(bodyWithMaxPerPeriod(-1))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithAvailableFromAfterAvailableUntilReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(bodyWithDateRange(LocalDate.of(2026, 12, 31),
						LocalDate.of(2026, 1, 1)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void adminCanGetById() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Get Admin"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-concepts/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()));
	}

	@Test
	void personalFinanzasCanGetById() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Get Finanzas"));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(get("/payment-concepts/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnGetById() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Get SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-concepts/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Get Unauth"));

		mockMvc.perform(get("/payment-concepts/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(
				get("/payment-concepts/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanUpdate() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Update Admin"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-concepts/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Inscripcion Renombrada"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Inscripcion Renombrada"));
	}

	@Test
	void personalFinanzasCanUpdate() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Update Finanzas"));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(put("/payment-concepts/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Renombrado por Finanzas"))))
				.andExpect(status().isOk());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnUpdate() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Update SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/payment-concepts/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUpdateReturns401() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Update Unauth"));

		mockMvc.perform(put("/payment-concepts/{id}", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Unauth"))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-concepts/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(validBody("Fantasma"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanChangeStatus() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Status Admin"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-concepts/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void personalFinanzasCanChangeStatus() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Status Finanzas"));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(patch("/payment-concepts/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnChangeStatus() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Status SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/payment-concepts/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.INACTIVE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Status Unauth"));

		mockMvc.perform(patch("/payment-concepts/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusIsIdempotentWhenTargetMatchesCurrentStatus() throws Exception {
		PaymentConcept saved = jpaRepository.save(newConcept("Inscripcion Status Idempotente"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-concepts/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.ACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-concepts/{id}/status", UUID.randomUUID())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentConceptStatus.INACTIVE))))
				.andExpect(status().isNotFound());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private static PaymentConcept newConcept(String name) {
		return new PaymentConcept(name, "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true, false, 1, 2,
				true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}

	private static CreateBody validBody(String name) {
		return new CreateBody(name, "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true, false, 1, 2,
				true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}

	private static CreateBody bodyWithMaxPerStudent(Integer maxPerStudent) {
		return new CreateBody("Concepto Invalido", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true,
				false, maxPerStudent, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}

	private static CreateBody bodyWithMaxPerPeriod(Integer maxPerPeriod) {
		return new CreateBody("Concepto Invalido", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true,
				false, 1, maxPerPeriod, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}

	private static CreateBody bodyWithDateRange(LocalDate availableFrom, LocalDate availableUntil) {
		return new CreateBody("Concepto Invalido", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true,
				false, 1, 2, true, availableFrom, availableUntil);
	}

	private record CreateBody(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
	}

	private record ChangeStatusBody(PaymentConceptStatus status) {
	}
}
