package mx.edu.utez.sisa.academic_config.infrastructure.web;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPeriodJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT for {@code POST /periods/advance-by-date} — the on-demand status refresh
 * the front triggers when the list opens, complementing the daily
 * {@code AdvanceAcademicPeriodStatusJob}. Real H2, real JWT filter chain, no
 * mocks. {@code @Transactional} rolls every test back: the advance walks ALL
 * periods in the database, so without rollback it would mature sibling tests'
 * fixtures (whose dates are all in the past) and make them flaky.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcademicPeriodAdvanceByDateIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private AcademicPeriodJpaRepository jpaRepository;

	@Test
	void adminAdvancesMaturedPeriodsOnDemand() throws Exception {
		AcademicPeriod matured = jpaRepository.save(newPastPeriod("Enero-Abril ADV-PAS", 2301, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/periods/advance-by-date").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.advanced").value(greaterThanOrEqualTo(1)));

		mockMvc.perform(get("/periods/{id}", matured.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void advanceByDateLeavesPeriodsWithFutureDatesInConfiguration() throws Exception {
		AcademicPeriod future = jpaRepository.save(newFuturePeriod("Enero-Abril ADV-FUT", 2302, 1));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/periods/advance-by-date").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/periods/{id}", future.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIGURATION"));
	}

	@Test
	void otherRoleIsForbiddenOnAdvanceByDate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/periods/advance-by-date").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedAdvanceByDateReturns401() throws Exception {
		mockMvc.perform(post("/periods/advance-by-date")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	private static AcademicPeriod newPastPeriod(String name, int year, int periodNumber) {
		return new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, LocalDate.of(2026, 1, 5),
				LocalDate.of(2026, 4, 30), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 20));
	}

	private static AcademicPeriod newFuturePeriod(String name, int year, int periodNumber) {
		LocalDate today = LocalDate.now();
		return new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, today.plusDays(30),
				today.plusDays(120), today.plusDays(5), today.plusDays(20));
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}
}