package mx.edu.utez.sisa.academic_config.infrastructure.job;

import mx.edu.utez.sisa.academic_config.domain.port.in.AdvanceAcademicPeriodStatusByDateUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Daily job that advances every {@code AcademicPeriod} through its lifecycle
 * as the clock passes each date threshold, complementing the manual
 * {@code PATCH /periods/{id}/status}: once {@code today >= enrollmentStart}
 * the period moves to {@code ENROLLMENT}, once {@code today >= startDate} to
 * {@code ACTIVE}, and once {@code today > endDate} to {@code CLOSED}. Runs at
 * 00:05 and only saves periods whose status actually changed; an idle
 * scheduler run logs nothing.
 */
@Component
public class AdvanceAcademicPeriodStatusJob {

	private static final Logger log = LoggerFactory.getLogger(AdvanceAcademicPeriodStatusJob.class);

	private final AdvanceAcademicPeriodStatusByDateUseCase advanceAcademicPeriodStatusByDateUseCase;

	public AdvanceAcademicPeriodStatusJob(
			AdvanceAcademicPeriodStatusByDateUseCase advanceAcademicPeriodStatusByDateUseCase) {
		this.advanceAcademicPeriodStatusByDateUseCase = advanceAcademicPeriodStatusByDateUseCase;
	}

	@Scheduled(cron = "0 5 0 * * *")
	public void advancePeriodStatuses() {
		int advanced = advanceAcademicPeriodStatusByDateUseCase.advanceAll(LocalDate.now());
		if (advanced > 0) {
			log.info("AdvanceAcademicPeriodStatusJob: advanced {} academic period(s) by date", advanced);
		}
	}
}