package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ExpireStaleFichaPaymentsUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Find-all-then-mutate-then-save shell for the daily ficha expiry: the actual
 * deadline arithmetic lives in {@link Candidate#paymentDeadline}, and this use
 * case only reads the admission clock, walks the {@code REGISTERED} fichas and
 * counts what changed. Same shape as
 * {@code AdvanceAcademicPeriodStatusByDateUseCaseImpl}.
 */
public class ExpireStaleFichaPaymentsUseCaseImpl implements ExpireStaleFichaPaymentsUseCase {

	private final CandidateRepository candidateRepository;
	private final Clock clock;
	private final int deadlineDays;

	public ExpireStaleFichaPaymentsUseCaseImpl(CandidateRepository candidateRepository, Clock clock,
			int deadlineDays) {
		this.candidateRepository = candidateRepository;
		this.clock = clock;
		this.deadlineDays = deadlineDays;
	}

	@Override
	@Transactional
	public int expireOverdue() {
		LocalDate today = LocalDate.now(clock);
		ZoneId zone = clock.getZone();
		int expired = 0;
		for (Candidate candidate : candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)) {
			if (today.isAfter(candidate.paymentDeadline(zone, deadlineDays)) && candidate.markPaymentExpired()) {
				candidateRepository.save(candidate);
				expired++;
			}
		}
		return expired;
	}
}
