package mx.edu.utez.sisa.admission.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.infrastructure.persistence.OutreachChannelJpaRepository;
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
 * End-to-end integration coverage for the {@code /outreach-channels} security
 * matchers: real H2, real JWT filter chain, no mocks — mirroring
 * {@code SubjectClassificationControllerIT}'s style. List/Get tests seed rows
 * directly via {@link OutreachChannelJpaRepository#save}; Create, Update and
 * ChangeStatus tests exercise the real endpoints end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OutreachChannelControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private OutreachChannelJpaRepository jpaRepository;

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(new OutreachChannel("Facebook-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(
				get("/outreach-channels").header("Authorization", "Bearer " + token).param("search", "Facebook-ADM"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void serviciosEscolaresCanList() throws Exception {
		jpaRepository.save(new OutreachChannel("Feria-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/outreach-channels").header("Authorization", "Bearer " + token).param("search", "Feria-SE"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/outreach-channels").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/outreach-channels")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Facebook Admin"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.name").value("Facebook Admin"));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Feria SE"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Forbidden"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/outreach-channels").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Unauth")))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void duplicateNameIsRejectedWith409() throws Exception {
		// El nombre normalizado es único desde la fase que copyó OutreachChannel
		// (antes este test afirmaba lo contrario: que el duplicado devolvía 201 dos
		// veces). Se comprueba el `code` estable y no el texto, que es lo que pide
		// el contrato de `ErrorResponse`.
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Instagram Dup"))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Instagram Dup"))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTREACH_CHANNEL_NAME_DUPLICATE"));
	}

	@Test
	void duplicateNameIsRejectedIgnoringCaseAndSurroundingWhitespace() throws Exception {
		// Los tres casos que la fase 9 nombró como criterio de aceptación:
		// "X", "x" y " X " son el mismo outreach channel. La segunda y la terceradifieren
		// sólo en mayúsculas y en espacios, y las tres deben dar 409.
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new CreateBody("Instagram Dup"))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/outreach-channels").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("  instagram dup  "))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTREACH_CHANNEL_NAME_DUPLICATE"));
	}

	@Test
	void adminCanGetById() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-GET-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/outreach-channels/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()))
				.andExpect(jsonPath("$.name").value("Facebook-GET-ADM"));
	}

	@Test
	void serviciosEscolaresCanGetById() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Feria-GET-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/outreach-channels/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Feria-GET-SE"));
	}

	@Test
	void otherRoleIsForbiddenOnGetById() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-GET-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/outreach-channels/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-GET-UNA"));

		mockMvc.perform(get("/outreach-channels/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(get("/outreach-channels/{id}", unknownId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanUpdate() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-UPD-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/outreach-channels/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Facebook renombrado"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Facebook renombrado"));
	}

	@Test
	void serviciosEscolaresCanUpdate() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Feria-UPD-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/outreach-channels/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new UpdateBody("Feria renombrada"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Feria renombrada"));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-UPD-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/outreach-channels/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new UpdateBody("Forbidden"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUpdateReturns401() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-UPD-UNA"));

		mockMvc.perform(put("/outreach-channels/{id}", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Unauth")))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(put("/outreach-channels/{id}", unknownId).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(objectMapper.writeValueAsString(new UpdateBody("Fantasma"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanChangeStatus() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-STA-ADM"));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/outreach-channels/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Feria-STA-SE"));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/outreach-channels/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-STA-DOC"));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/outreach-channels/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.INACTIVE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook-STA-UNA"));

		mockMvc.perform(patch("/outreach-channels/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID unknownId = UUID.randomUUID();

		mockMvc.perform(patch("/outreach-channels/{id}/status", unknownId)
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(OutreachChannelStatus.INACTIVE))))
				.andExpect(status().isNotFound());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(String name) {
	}

	private record UpdateBody(String name) {
	}

	private record ChangeStatusBody(OutreachChannelStatus status) {
	}
}
