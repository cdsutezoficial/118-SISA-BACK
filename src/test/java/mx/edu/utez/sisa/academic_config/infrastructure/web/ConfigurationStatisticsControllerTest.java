package mx.edu.utez.sisa.academic_config.infrastructure.web;

import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase.CurrentPeriodStatistics;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase.GetConfigurationStatisticsResult;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller test for {@link ConfigurationStatisticsController}: one
 * delegate to {@link GetConfigurationStatisticsUseCase}, mirroring
 * {@code AcademicDivisionControllerTest} (no security filters — the coarse
 * role matcher lives in {@code SecurityFilterConfig}, out of {@code WebMvcTest}
 * scope).
 */
@WebMvcTest(ConfigurationStatisticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConfigurationStatisticsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GetConfigurationStatisticsUseCase getConfigurationStatisticsUseCase;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	@Test
	void statistics_returnsAllCountersWithCurrentPeriod() throws Exception {
		UUID periodId = UUID.randomUUID();
		when(getConfigurationStatisticsUseCase.getStatistics())
				.thenReturn(new GetConfigurationStatisticsResult(4, 12, 148, 36,
						new CurrentPeriodStatistics(periodId, "Mayo-Agosto 2026")));

		mockMvc.perform(get("/config-academica/statistics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.divisions").value(4))
				.andExpect(jsonPath("$.programs").value(12))
				.andExpect(jsonPath("$.subjects").value(148))
				.andExpect(jsonPath("$.groupsForCurrentPeriod").value(36))
				.andExpect(jsonPath("$.currentPeriod.id").value(periodId.toString()))
				.andExpect(jsonPath("$.currentPeriod.name").value("Mayo-Agosto 2026"));
	}

	@Test
	void statistics_omitsCurrentPeriodWhenNoPeriodIsConfigured() throws Exception {
		when(getConfigurationStatisticsUseCase.getStatistics())
				.thenReturn(new GetConfigurationStatisticsResult(0, 0, 0, 0, null));

		mockMvc.perform(get("/config-academica/statistics"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.groupsForCurrentPeriod").value(0))
				.andExpect(jsonPath("$.currentPeriod").doesNotExist());
	}
}