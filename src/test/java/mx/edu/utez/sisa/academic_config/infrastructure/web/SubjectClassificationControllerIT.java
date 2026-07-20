package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.SubjectClassificationJpaRepository;
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
 * End-to-end integration coverage for the {@code /subject-classifications}
 * security matchers (Phase 1 — List, Phase 2 — Create, Phase 3 — Get by id,
 * Phase 4 — Update, Phase 5 — ChangeStatus): real H2, real JWT filter chain,
 * no mocks — mirroring {@code AcademicDivisionControllerIT}'s style.
 * List/Get tests still seed rows directly via
 * {@link SubjectClassificationJpaRepository#save}; Create, Update and
 * ChangeStatus tests exercise the real endpoints end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubjectClassificationControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private SubjectClassificationJpaRepository jpaRepository;

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(new SubjectClassification("Integradora", "INT-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/subject-classifications").header("Authorization", "Bearer " + token)
				.param("search", "INT-ADM")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void serviciosEscolaresCanList() throws Exception {
		jpaRepository.save(new SubjectClassification("Regular", "REG-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/subject-classifications").header("Authorization", "Bearer " + token)
				.param("search", "REG-SE")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/subject-classifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/subject-classifications")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/subject-classifications").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Integradora Admin", "INT-C-ADM"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.code").value("INT-C-ADM"));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/subject-classifications").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Integradora SE", "INT-C-SE"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/subject-classifications").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Forbidden", "INT-C-FRB"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/subject-classifications").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Unauth", "INT-C-UNA"))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void duplicateCodeReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/subject-classifications").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Integradora Dup", "INT-C-DUP"))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/subject-classifications").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Otro Nombre", "INT-C-DUP"))))
				.andExpect(status().isConflict());
	}

	@Test
	void adminCanGetById() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-GET-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()))
				.andExpect(jsonPath("$.code").value("INT-GET-ADM"));
	}

	@Test
	void serviciosEscolaresCanGetById() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Regular", "REG-GET-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("REG-GET-SE"));
	}

	@Test
	void otherRoleIsForbiddenOnGetById() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-GET-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-GET-UNA"));

		mockMvc.perform(get("/subject-classifications/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(get("/subject-classifications/{id}", unknownId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanUpdate() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-UPD-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Integradora renombrada", "INT-UPD-ADM-2"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Integradora renombrada"))
				.andExpect(jsonPath("$.code").value("INT-UPD-ADM-2"));
	}

	@Test
	void serviciosEscolaresCanUpdate() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Regular", "REG-UPD-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Regular renombrada", "REG-UPD-SE-2"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("REG-UPD-SE-2"));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-UPD-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Forbidden", "INT-UPD-DOC-2"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUpdateReturns401() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-UPD-UNA"));

		mockMvc.perform(put("/subject-classifications/{id}", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Unauth", "INT-UPD-UNA-2"))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(put("/subject-classifications/{id}", unknownId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Fantasma", "INT-UPD-404"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateWithCodeCollidingWithAnotherRecordReturns409() throws Exception {
		jpaRepository.save(new SubjectClassification("Integradora", "INT-UPD-DUP-A"));
		SubjectClassification target = jpaRepository.save(new SubjectClassification("Regular", "REG-UPD-DUP-B"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(
				put("/subject-classifications/{id}", target.getId()).header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new UpdateBody("Regular", "INT-UPD-DUP-A"))))
				.andExpect(status().isConflict());
	}

	@Test
	void updateWithUnchangedCodeOnOwnRecordSucceeds() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-UPD-SELF"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/subject-classifications/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Integradora renombrada", "INT-UPD-SELF"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Integradora renombrada"))
				.andExpect(jsonPath("$.code").value("INT-UPD-SELF"));
	}

	@Test
	void adminCanChangeStatus() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-STA-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/subject-classifications/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ClassificationStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Regular", "REG-STA-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/subject-classifications/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ClassificationStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-STA-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/subject-classifications/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ClassificationStatus.INACTIVE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		SubjectClassification saved = jpaRepository.save(new SubjectClassification("Integradora", "INT-STA-UNA"));

		mockMvc.perform(patch("/subject-classifications/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ClassificationStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(patch("/subject-classifications/{id}/status", unknownId)
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ClassificationStatus.INACTIVE))))
				.andExpect(status().isNotFound());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(String name, String code) {
	}

	private record UpdateBody(String name, String code) {
	}

	private record ChangeStatusBody(ClassificationStatus status) {
	}
}
