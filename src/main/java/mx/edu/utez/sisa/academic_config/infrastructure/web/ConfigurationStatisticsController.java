package mx.edu.utez.sisa.academic_config.infrastructure.web;

import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase.CurrentPeriodStatistics;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase.GetConfigurationStatisticsResult;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ConfigurationStatisticsResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CurrentPeriodResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller answering the dataset self-service dashboard's KPI counters
 * via a single {@code GET /config-academica/statistics} call, so the frontend
 * issues no per-module queries (config-académica). Role authorization (ADMIN
 * or SERVICIOS_ESCOLARES) is enforced by {@code SecurityFilterConfig}, not
 * here. This endpoint is deliberately NOT registered in
 * {@code PermissionRegistry} — like every {@code /options}/{@code reference}
 * read, the coarse matcher is the only role gate.
 */
@RestController
@RequestMapping("/config-academica/statistics")
public class ConfigurationStatisticsController {

	private final GetConfigurationStatisticsUseCase getConfigurationStatisticsUseCase;

	public ConfigurationStatisticsController(GetConfigurationStatisticsUseCase getConfigurationStatisticsUseCase) {
		this.getConfigurationStatisticsUseCase = getConfigurationStatisticsUseCase;
	}

	@GetMapping
	public ResponseEntity<ConfigurationStatisticsResponse> statistics() {
		GetConfigurationStatisticsResult result = getConfigurationStatisticsUseCase.getStatistics();
		return ResponseEntity.ok(toResponse(result));
	}

	private static ConfigurationStatisticsResponse toResponse(GetConfigurationStatisticsResult result) {
		if (result.currentPeriod() == null) {
			return new ConfigurationStatisticsResponse(result.divisions(), result.programs(), result.subjects(),
					result.groupsForCurrentPeriod(), null);
		}
		CurrentPeriodStatistics period = result.currentPeriod();
		return new ConfigurationStatisticsResponse(result.divisions(), result.programs(), result.subjects(),
				result.groupsForCurrentPeriod(), new CurrentPeriodResponse(period.id(), period.name()));
	}
}