package mx.edu.utez.sisa.shared.web;

import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code GET /municipalities?stateId=} — real H2,
 * real seeded data, real JWT filter chain. Confirms the
 * {@code authenticated()}-only, no-role-restriction security matcher, the
 * required {@code stateId} param (400 if missing — via
 * {@code identity.GlobalExceptionHandler}'s new
 * {@code MissingServletRequestParameterException} handler), and the
 * Morelos/Emiliano Zapata spot-check via the API itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MunicipalityControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private StateJpaRepository stateRepository;

	@Test
	void anyAuthenticatedRoleCanListMorelosMunicipalitiesIncludingEmilianoZapata() throws Exception {
		UUID morelosId = stateRepository.findAll().stream().filter(state -> state.getInegiCode().equals("17"))
				.findFirst().orElseThrow().getId();
		String token = jwtService.sign(UUID.randomUUID().toString(), Set.of(RoleType.DOCENTE.name()));

		mockMvc.perform(get("/municipalities").header("Authorization", "Bearer " + token).param("stateId",
				morelosId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(36))
				.andExpect(jsonPath("$.items[?(@.inegiCode == '008')].name").value("Emiliano Zapata"));
	}

	@Test
	void missingStateIdReturns400() throws Exception {
		String token = jwtService.sign(UUID.randomUUID().toString(), Set.of(RoleType.ADMIN.name()));

		mockMvc.perform(get("/municipalities").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		UUID morelosId = stateRepository.findAll().stream().filter(state -> state.getInegiCode().equals("17"))
				.findFirst().orElseThrow().getId();

		mockMvc.perform(get("/municipalities").param("stateId", morelosId.toString()))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}
}
