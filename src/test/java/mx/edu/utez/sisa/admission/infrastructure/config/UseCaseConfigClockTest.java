package mx.edu.utez.sisa.admission.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UseCaseConfig#clock(String)} decides which calendar the sales-window rule
 * is evaluated on, so its zone is behaviour, not plumbing. These tests exist
 * because the obvious implementation — {@code Clock.systemDefaultZone()} — passes
 * them on a developer machine in Mexico City and quietly breaks on a server in
 * UTC, which is the worst possible failure mode: right answers locally, wrong
 * dates in production, and nothing in the logs.
 */
class UseCaseConfigClockTest {

	private final UseCaseConfig config = new UseCaseConfig();

	@Test
	void pinsTheAdmissionCalendarToTheConfiguredZone() {
		Clock clock = config.clock("America/Mexico_City");

		assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Mexico_City"));
	}

	@Test
	void honoursAnOverriddenZone() {
		// The same stored instant has to be readable in a different calendar when
		// the deployment says so; otherwise the fix for one environment breaks
		// another.
		assertThat(config.clock("Europe/Madrid").getZone()).isEqualTo(ZoneId.of("Europe/Madrid"));
	}

	@Test
	void theZoneDecidesWhichCalendarDateAStoredInstantFallsOn() {
		// One row, two readings. Mexico City is behind UTC, so an instant at
		// 00:00 UTC is still the *previous* day locally — the off-by-one-day an
		// applicant would be told to come back on if the sales window were
		// evaluated on the JVM's zone instead of the configured one.
		Instant stored = Instant.parse("2026-09-01T00:00:00Z");

		assertThat(stored.atZone(config.clock("America/Mexico_City").getZone()).toLocalDate())
				.hasToString("2026-08-31");
		assertThat(stored.atZone(ZoneOffset.UTC).toLocalDate()).hasToString("2026-09-01");
	}
}
