package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentAreaJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /payment-areas} security
 * matchers: real H2, real JWT filter chain, no mocks. Same
 * {@code ADMIN}/{@code PERSONAL_FINANZAS} pair as {@code /payment-concepts} —
 * {@code SERVICIOS_ESCOLARES} is deliberately NOT granted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAreaControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PaymentAreaJpaRepository jpaRepository;

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(newArea("Lista Admin", "LA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-areas").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
	}

	@Test
	void personalFinanzasCanList() throws Exception {
		jpaRepository.save(newArea("Lista Finanzas", "LF" + suffix()));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(get("/payment-areas").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-areas").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/payment-areas")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Admin", "AA" + suffix()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void personalFinanzasCanCreate() throws Exception {
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Finanzas", "AF" + suffix()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden SE", "FSE" + suffix()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden Docente", "FD" + suffix()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Unauth", "UN" + suffix()))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createWithDuplicateNameReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		String code = "DN" + suffix();
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Duplicada Nombre", code))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Duplicada Nombre", "DN2" + suffix()))))
				.andExpect(status().isConflict());
	}

	@Test
	void createWithDuplicateCodeReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		String code = "DC" + suffix();
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Codigo Uno", code))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Codigo Dos", code))))
				.andExpect(status().isConflict());
	}

	@Test
	void createWithBlankNameReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("", "BN" + suffix()))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void adminCanGetById() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Get Admin", "GA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnGetById() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Get SE", "GS" + suffix()));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Get Unauth", "GU" + suffix()));

		mockMvc.perform(get("/payment-areas/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-areas/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanUpdate() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Update Admin", "UA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Renombrada", saved.getCode()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Area Renombrada"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnUpdate() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Update SE", "US" + suffix()));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden", saved.getCode()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-areas/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Fantasma", "FA" + suffix()))))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanChangeStatus() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status Admin", "SA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void personalFinanzasCanChangeStatus() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status Finanzas", "SF" + suffix()));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnChangeStatus() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status SE", "SS" + suffix()));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status Unauth", "SU" + suffix()));

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-areas/{id}/status", UUID.randomUUID())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isNotFound());
	}

	@Test
	void optionsAreAccessibleToAnyAuthenticatedRole() throws Exception {
		jpaRepository.save(newArea("Opciones Docente", "OD" + suffix()));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/payment-areas/options").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
	}

	@Test
	void unauthenticatedOptionsReturns401() throws Exception {
		mockMvc.perform(get("/payment-areas/options")).andExpect(status().isUnauthorized());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static PaymentArea newArea(String name, String code) {
		return new PaymentArea(name, code, "Descripcion");
	}

	private static CreateBody validBody(String name, String code) {
		return new CreateBody(name, code, "Descripcion");
	}

	private record CreateBody(String name, String code, String description) {
	}

	private record ChangeStatusBody(PaymentAreaStatus status) {
	}
}
