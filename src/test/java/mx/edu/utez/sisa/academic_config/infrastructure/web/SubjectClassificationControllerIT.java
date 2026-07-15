package mx.edu.utez.sisa.academic_config.infrastructure.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /subject-classifications}
 * security matcher (Phase 1 — List only): real H2, real JWT filter chain, no
 * mocks — mirroring {@code AcademicDivisionControllerIT}'s style. Rows are
 * seeded directly via {@link SubjectClassificationJpaRepository#save} since
 * this phase has no {@code POST} endpoint yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubjectClassificationControllerIT {

	@Autowired
	private MockMvc mockMvc;

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

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}
}
