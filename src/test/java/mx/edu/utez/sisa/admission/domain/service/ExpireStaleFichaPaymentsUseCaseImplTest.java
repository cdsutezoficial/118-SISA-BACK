package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
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

	private ExpireStaleFichaPaymentsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ExpireStaleFichaPaymentsUseCaseImpl(candidateRepository, CLOCK, DEADLINE_DAYS);
	}

	private static Candidate registeredDaysAgo(long days) {
		Candidate candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		Instant registeredAt = TODAY.minusDays(days).atStartOfDay(ZONE).toInstant();
		ReflectionTestUtils.setField(candidate, "registeredAt", registeredAt);
		return candidate;
	}

	@Test
	void expireOverdue_expiresFichasPastTheirWindow_andOnlySavesThose() {
		Candidate expired = registeredDaysAgo(DEADLINE_DAYS + 1);
		Candidate stillValid = registeredDaysAgo(3);
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
		Candidate lastDay = registeredDaysAgo(DEADLINE_DAYS);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of(lastDay));

		int changed = useCase.expireOverdue();

		assertThat(changed).isZero();
		assertThat(lastDay.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
		verify(candidateRepository, never()).save(any());
	}

	@Test
	void expireOverdue_expiresOnlyFromTheDayAfterTheDeadline() {
		Candidate firstDayPastDeadline = registeredDaysAgo(DEADLINE_DAYS + 1);
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of(firstDayPastDeadline));

		int changed = useCase.expireOverdue();

		assertThat(changed).isEqualTo(1);
		assertThat(firstDayPastDeadline.getStatus()).isEqualTo(CandidateStatus.PAYMENT_EXPIRED);
	}

	@Test
	void expireOverdue_returnsZero_whenThereAreNoRegisteredFichas() {
		when(candidateRepository.findAllByStatus(CandidateStatus.REGISTERED)).thenReturn(List.of());

		assertThat(useCase.expireOverdue()).isZero();
		verify(candidateRepository, never()).save(any());
	}
}
