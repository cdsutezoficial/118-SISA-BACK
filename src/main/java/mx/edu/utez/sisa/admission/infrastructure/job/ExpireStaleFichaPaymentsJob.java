package mx.edu.utez.sisa.admission.infrastructure.job;

import mx.edu.utez.sisa.admission.domain.port.in.ExpireStaleFichaPaymentsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job (VENCEN_FICHAS) that expires every ficha whose payment window passed
 * without payment, releasing its CURP per §1.10. Runs at 00:10, five minutes
 * after {@code AdvanceAcademicPeriodStatusJob}, and only logs when something
 * actually expired. The use case owns the deadline arithmetic; this class is
 * the scheduler adapter, mirroring {@code AdvanceAcademicPeriodStatusJob}.
 */
@Component
public class ExpireStaleFichaPaymentsJob {

	private static final Logger log = LoggerFactory.getLogger(ExpireStaleFichaPaymentsJob.class);

	private final ExpireStaleFichaPaymentsUseCase expireStaleFichaPaymentsUseCase;

	public ExpireStaleFichaPaymentsJob(ExpireStaleFichaPaymentsUseCase expireStaleFichaPaymentsUseCase) {
		this.expireStaleFichaPaymentsUseCase = expireStaleFichaPaymentsUseCase;
	}

	@Scheduled(cron = "0 10 0 * * *")
	public void expireStaleFichas() {
		int expired = expireStaleFichaPaymentsUseCase.expireOverdue();
		if (expired > 0) {
			log.info("ExpireStaleFichaPaymentsJob: {} ficha(s) pasaron a PAYMENT_EXPIRED", expired);
		}
	}
}
