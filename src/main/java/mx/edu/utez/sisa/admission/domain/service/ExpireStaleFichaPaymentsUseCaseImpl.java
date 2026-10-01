package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ExpireStaleFichaPaymentsUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Find-all-then-mutate-then-save shell for the daily ficha expiry. It holds no
 * date arithmetic of its own: the deadline is
 * {@link FichaPaymentWindow#deadlineOf}, the same function the checkout gate and
 * the CURP lock use, and this class only walks the {@code REGISTERED} fichas
 * and counts what changed. Same shape as
 * {@code AdvanceAcademicPeriodStatusByDateUseCaseImpl}.
 *
 * <p><b>This job is the only writer of {@code PAYMENT_EXPIRED}</b>, which is why
 * nothing else may expire a ficha by writing the status. Two readers depend on
 * that: the CURP lock treats {@code REGISTERED} past its window as already free
 * precisely so the lock stays correct in the gap before the next run, and the
 * occupancy queries release a lapsed claim from dates rather than from a sweep.
 * If a second writer appeared, both of those would be repairing something that
 * should not have been broken.
 */
public class ExpireStaleFichaPaymentsUseCaseImpl implements ExpireStaleFichaPaymentsUseCase {

	private final CandidateRepository candidateRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final Clock clock;
	private final int deadlineDays;

	public ExpireStaleFichaPaymentsUseCaseImpl(CandidateRepository candidateRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort, Clock clock, int deadlineDays) {
		this.candidateRepository = candidateRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.clock = clock;
		this.deadlineDays = deadlineDays;
	}

	/**
	 * Expires every {@code REGISTERED} ficha whose payment window is over.
	 *
	 * <p>The window is the earlier of the ficha's own plazo and the closing day of
	 * its admission process — {@link FichaPaymentWindow#deadlineOf}. It used to be
	 * the ficha's own plazo alone, which meant a process could close on the 20th
	 * and keep every unpaid ficha's state as "registered" until the last of them
	 * reached ten days: the candidate screen said "you can still pay" for a sale
	 * that had ended, and the checkout then refused her with a message about her
	 * own window that was not the reason.
	 *
	 * <p>Both bounds are read on the ficha's own terms — registration day in the
	 * admission zone, closing day in the same zone — so a ficha issued on the 28th
	 * in a process closing on the 30th survives both, and a ficha issued on the
	 * 20th in the same process does not.
	 *
	 * <p>The whole run is one transaction and the count is returned for the job log.
	 * A single rollback is the right failure mode here: half-expired fichas would
	 * leave the portal showing two different answers for the same rule, and the
	 * next night's run would fix it silently, which is worse than a loud retry.
	 */
	@Override
	@Transactional
	public int expireOverdue() {
		LocalDate today = LocalDate.now(clock);
		ZoneId zone = clock.getZone();
		int expired = 0;

		// One lookup per admission process, not per ficha: the sweep walks every
		// REGISTERED candidate in the system and they all share a handful of configs.
		// Memoised through Optional because computeIfAbsent does not store a null,
		// which would turn a missing config into one query per candidate.
		Map<UUID, Optional<LocalDate>> closesByConfig = new HashMap<>();

		for (Candidate candidate : candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)) {
			LocalDate closesOn = closesByConfig
					.computeIfAbsent(candidate.getAdmissionConfigId(), this::closesOnOf)
					.orElse(null);
			if (today.isAfter(FichaPaymentWindow.deadlineOf(candidate, closesOn, deadlineDays, zone))
					&& candidate.markPaymentExpired()) {
				candidateRepository.save(candidate);
				expired++;
			}
		}
		return expired;
	}

	/**
	 * The calendar day a process stops selling, in the admission zone.
	 *
	 * <p>{@code closesAt} is an {@code Instant} and which day it lands on is a
	 * question about the university's day, not the host's — the same reason
	 * {@code RegisterCandidateUseCaseImpl} converts it through the clock's zone.
	 *
	 * <p>An absent config answers empty rather than throwing. This sweep runs
	 * unattended at 00:10 against every historical ficha, and one orphaned config
	 * must not abort the night's work for everyone else; the ficha then falls back
	 * to its own plazo alone, which is the conservative direction — it expires
	 * later, never earlier than it should.
	 */
	private Optional<LocalDate> closesOnOf(UUID admissionConfigId) {
		return programAdmissionConfigQueryPort.findById(admissionConfigId)
				.map(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo::closesAt)
				.map(closesAt -> closesAt.atZone(clock.getZone()).toLocalDate());
	}
}
