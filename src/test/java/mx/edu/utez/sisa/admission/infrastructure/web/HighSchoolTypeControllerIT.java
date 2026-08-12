package mx.edu.utez.sisa.admission.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.infrastructure.persistence.HighSchoolTypeJpaRepository;
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
 * End-to-end integration coverage for the {@code /high-school-types} security
 * matchers: real H2, real JWT filter chain, no mocks — mirroring
 * {@code OutreachChannelControllerIT}'s style.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HighSchoolTypeControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private HighSchoolTypeJpaRepository jpaRepository;

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(new HighSchoolType("Conalep-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(
				get("/high-school-types").header("Authorization", "Bearer " + token).param("search", "Conalep-ADM"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void serviciosEscolaresCanList() throws Exception {
		jpaRepository.save(new HighSchoolType("Cobaem-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/high-school-types").header("Authorization", "Bearer " + token).param("search", "Cobaem-SE"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/high-school-types").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/high-school-types")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/high-school-types").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Conalep Admin"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.name").value("Conalep Admin"));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/high-school-types").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Cobaem SE"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/high-school-types").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Forbidden"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/high-school-types").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Unauth")))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void duplicateNameIsAllowedAndReturns201Twice() throws Exception {
		// name has NO uniqueness constraint on this aggregate (see
		// HighSchoolType's javadoc) — creating the same name twice must
		// succeed both times.
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/high-school-types").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Bachillerato Dup"))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/high-school-types").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Bachillerato Dup"))))
				.andExpect(status().isCreated());
	}

	@Test
	void adminCanGetById() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-GET-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/high-school-types/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()))
				.andExpect(jsonPath("$.name").value("Conalep-GET-ADM"));
	}

	@Test
	void serviciosEscolaresCanGetById() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Cobaem-GET-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/high-school-types/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cobaem-GET-SE"));
	}

	@Test
	void otherRoleIsForbiddenOnGetById() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-GET-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/high-school-types/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-GET-UNA"));

		mockMvc.perform(get("/high-school-types/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(get("/high-school-types/{id}", unknownId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanUpdate() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-UPD-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/high-school-types/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Conalep renombrado"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Conalep renombrado"));
	}

	@Test
	void serviciosEscolaresCanUpdate() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Cobaem-UPD-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/high-school-types/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new UpdateBody("Cobaem renombrado"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cobaem renombrado"));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-UPD-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/high-school-types/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new UpdateBody("Forbidden"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUpdateReturns401() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-UPD-UNA"));

		mockMvc.perform(put("/high-school-types/{id}", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Unauth")))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(put("/high-school-types/{id}", unknownId).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new UpdateBody("Fantasma"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanChangeStatus() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-STA-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/high-school-types/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Cobaem-STA-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/high-school-types/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-STA-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/high-school-types/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.INACTIVE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		HighSchoolType saved = jpaRepository.save(new HighSchoolType("Conalep-STA-UNA"));

		mockMvc.perform(patch("/high-school-types/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(patch("/high-school-types/{id}/status", unknownId)
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.INACTIVE))))
				.andExpect(status().isNotFound());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(String name) {
	}

	private record UpdateBody(String name) {
	}

	private record ChangeStatusBody(HighSchoolTypeStatus status) {
	}
}
