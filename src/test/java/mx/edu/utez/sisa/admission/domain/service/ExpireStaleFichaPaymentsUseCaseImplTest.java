package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpireStaleFichaPaymentsUseCaseImplTest {

	private static final int DEADLINE_DAYS = 10;
	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
	private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private ExpireStaleFichaPaymentsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ExpireStaleFichaPaymentsUseCaseImpl(candidateRepository, programAdmissionConfigQueryPort, CLOCK,
				DEADLINE_DAYS);
	}

	/**
	 * The process closes on {@code closesOn}; {@code null} answers a config that no
	 * longer exists, which is a different situation from one that closes late.
	 */
	private void closesOn(UUID configId, LocalDate closesOn) {
		when(programAdmissionConfigQueryPort.findById(configId)).thenReturn(Optional
				.of(config(configId, closesOn == null ? TODAY.plusDays(365) : closesOn)));
	}

	private static AdmissionConfigInfo config(UUID id, LocalDate closesOn) {
		return new AdmissionConfigInfo(id, ProgramAdmissionConfigStatus.OPEN, UUID.randomUUID(), "Ing. en TIC",
				null, ProgramModality.PRESENCIAL, "2027-I", TODAY.minusDays(60).atStartOfDay(ZONE).toInstant(),
				closesOn.atStartOfDay(ZONE).toInstant(), 120);
	}

	private static Candidate registeredDaysAgo(UUID configId, long days) {
		Candidate candidate = new Candidate(UUID.randomUUID(), configId, "ADM-2026-000001", true, true, null);
		Instant registeredAt = TODAY.minusDays(days).atStartOfDay(ZONE).toInstant();
		ReflectionTestUtils.setField(candidate, "registeredAt", registeredAt);
		return candidate;
	}

	@Test
	void expireOverdue_expiresFichasPastTheirWindow_andOnlySavesThose() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY.plusDays(30));
		Candidate expired = registeredDaysAgo(configId, DEADLINE_DAYS + 1);
		Candidate stillValid = registeredDaysAgo(configId, 3);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED))
				.thenReturn(List.of(expired, stillValid));

		int changed = useCase.expireOverdue();

		assertThat(changed).isEqualTo(1);
		assertThat(expired.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
		assertThat(stillValid.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
		verify(candidateRepository).save(expired);
		verify(candidateRepository, never()).save(stillValid);
	}

	@Test
	void expireOverdue_keepsTheDeadlineDayPayable() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY.plusDays(30));
		Candidate lastDay = registeredDaysAgo(configId, DEADLINE_DAYS);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of(lastDay));

		int changed = useCase.expireOverdue();

		assertThat(changed).isZero();
		assertThat(lastDay.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
		verify(candidateRepository, never()).save(any());
	}

	@Test
	void expireOverdue_expiresOnlyFromTheDayAfterTheDeadline() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY.plusDays(30));
		Candidate firstDayPastDeadline = registeredDaysAgo(configId, DEADLINE_DAYS + 1);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED))
				.thenReturn(List.of(firstDayPastDeadline));

		int changed = useCase.expireOverdue();

		assertThat(changed).isEqualTo(1);
		assertThat(firstDayPastDeadline.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
	}

	/**
	 * The defect this phase fixes: a process closing today ends every unpaid ficha
	 * in it, even for applicants whose own ten days are not up. Before this the
	 * sweep only knew the ficha's own window, so the portal kept offering payment
	 * for a sale that had ended and the checkout refused it with the wrong reason.
	 */
	@Test
	void expireOverdue_expiresAFichaWhoseProcessClosedBeforeItsOwnWindow() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY.minusDays(1));
		Candidate fresh = registeredDaysAgo(configId, 1);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of(fresh));

		int changed = useCase.expireOverdue();

		assertThat(changed).isEqualTo(1);
		assertThat(fresh.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
	}

	/**
	 * The closing day itself is still payable. {@code closesAt} is stored as an
	 * instant, so a config closing at 23:00 on the 30th must not expire a ficha
	 * earlier in that same day.
	 */
	@Test
	void expireOverdue_keepsAFichaPayableOnTheDayItsProcessCloses() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY);
		Candidate sameDay = registeredDaysAgo(configId, 1);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of(sameDay));

		assertThat(useCase.expireOverdue()).isZero();
		assertThat(sameDay.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
	}

	/**
	 * A process that closes after the ficha's own window leaves the ficha's own
	 * plazo as the binding bound — the closing date must not hand anybody extra
	 * days.
	 */
	@Test
	void expireOverdue_usesTheFichasOwnWindowWhenTheProcessClosesLater() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY.plusDays(30));
		Candidate expiredByOwnWindow = registeredDaysAgo(configId, DEADLINE_DAYS + 1);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED))
				.thenReturn(List.of(expiredByOwnWindow));

		assertThat(useCase.expireOverdue()).isEqualTo(1);
		assertThat(expiredByOwnWindow.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
	}

	/**
	 * A deleted config must not abort the night's sweep, and must not expire
	 * anyone early either: the ficha falls back to its own window alone.
	 */
	@Test
	void expireOverdue_fallsBackToTheOwnWindow_whenTheConfigIsGone() {
		UUID configId = UUID.randomUUID();
		when(programAdmissionConfigQueryPort.findById(configId)).thenReturn(Optional.empty());
		Candidate expiredByOwnWindow = registeredDaysAgo(configId, DEADLINE_DAYS + 1);
		Candidate stillOwnValid = registeredDaysAgo(configId, 2);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED))
				.thenReturn(List.of(expiredByOwnWindow, stillOwnValid));

		int changed = useCase.expireOverdue();

		assertThat(changed).isEqualTo(1);
		assertThat(expiredByOwnWindow.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
		assertThat(stillOwnValid.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
	}

	/**
	 * The sweep walks every REGISTERED ficha in the system and they share a handful
	 * of processes, so the config must be read once per process and not once per
	 * ficha.
	 */
	@Test
	void expireOverdue_readsEachProcessClosingDateOnce() {
		UUID configId = UUID.randomUUID();
		closesOn(configId, TODAY.plusDays(30));
		Candidate first = registeredDaysAgo(configId, 1);
		Candidate second = registeredDaysAgo(configId, 2);
		Candidate third = registeredDaysAgo(configId, 3);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED))
				.thenReturn(List.of(first, second, third));

		useCase.expireOverdue();

		verify(programAdmissionConfigQueryPort).findById(configId);
	}

	@Test
	void expireOverdue_returnsZero_whenThereAreNoRegisteredFichas() {
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of());

		assertThat(useCase.expireOverdue()).isZero();
		verify(candidateRepository, never()).save(any());
	}
}
