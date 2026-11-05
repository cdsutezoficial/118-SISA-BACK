package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.StaffDirectoryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.NothingToCollectException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentInProgressException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectCashPaymentUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-05T17:00:00Z");

	private static final BigDecimal PRICE = new BigDecimal("500.00");

	private static final UUID CHARGE_ID = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	@Mock
	private FichaPaymentGate fichaPaymentGate;

	@Mock
	private PendingCheckoutGuard pendingCheckoutGuard;

	@Mock
	private AdmissionQuotaPort admissionQuotaPort;

	@Mock
	private ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	@Mock
	private StaffDirectoryPort staffDirectory;

	private CollectCashPaymentUseCaseImpl useCase;

	private UUID candidateId;

	private Candidate candidate;

	@BeforeEach
	void setUp() {
		useCase = new CollectCashPaymentUseCaseImpl(candidateRepository, fichaPaymentPort, fichaPaymentGate,
				pendingCheckoutGuard, admissionQuotaPort, confirmAdmissionPaymentUseCase, staffDirectory,
				Clock.fixed(NOW, ZoneOffset.UTC), 60);
		candidateId = UUID.randomUUID();
		candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		candidate.linkFichaPayment(CHARGE_ID);
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
	}

	private void stubOwedFicha(BigDecimal amountDue, int occupied) {
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		when(fichaPaymentGate.requireOpen(candidate)).thenReturn(realConfig());
		when(fichaPaymentPort.quote(CHARGE_ID)).thenReturn(new FichaPaymentPort.FichaQuote(CHARGE_ID, "ADM",
				"Ficha", null, PRICE, PRICE.subtract(amountDue), amountDue, null, amountDue.signum() == 0, true, null));
		if (amountDue.signum() > 0) {
			when(admissionQuotaPort.countOccupiedExcluding(eq(CONFIG_ID), eq(candidateId), any()))
					.thenReturn((long) occupied);
		}
	}

	private static final UUID CONFIG_ID = UUID.randomUUID();

	private static ProgramAdmissionConfigQueryPort.AdmissionConfigInfo realConfig() {
		return new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(CONFIG_ID, null, UUID.randomUUID(), "TIC",
				"DATIC", null, "2026", NOW.minusSeconds(86400), NOW.plusSeconds(86400 * 30), 10);
	}

	@Test
	void preview_saysHowMuchToTakeBeforeTheMoneyIsReceived() {
		stubOwedFicha(PRICE, 9);

		var preview = useCase.preview(candidateId);

		assertThat(preview.toCollect()).isEqualByComparingTo(PRICE);
		assertThat(preview.alreadyPaid()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(preview.programChange()).isFalse();
		verify(pendingCheckoutGuard).settleBeforeCashPayment(candidate);
	}

	@Test
	void preview_refusesAFullCareer() {
		stubOwedFicha(PRICE, 10);

		assertThatThrownBy(() -> useCase.preview(candidateId))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class);
	}

	@Test
	void preview_refusesOutsideTheWindow() {
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		when(fichaPaymentGate.requireOpen(candidate)).thenThrow(new FichaPaymentExpiredException("venció"));

		assertThatThrownBy(() -> useCase.preview(candidateId)).isInstanceOf(FichaPaymentExpiredException.class);
		verify(admissionQuotaPort, never()).countOccupiedExcluding(any(), any(), any());
	}

	@Test
	void preview_refusesAFichaAFullDiscountCovers() {
		stubOwedFicha(BigDecimal.ZERO, 0);

		assertThatThrownBy(() -> useCase.preview(candidateId)).isInstanceOf(NothingToCollectException.class);
	}

	@Test
	void preview_refusesWhileAnOnlineSessionCanStillCapture() {
		doThrow(new PaymentInProgressException("en curso")).when(pendingCheckoutGuard)
				.settleBeforeCashPayment(candidate);

		assertThatThrownBy(() -> useCase.preview(candidateId)).isInstanceOf(PaymentInProgressException.class);
	}

	@Test
	void preview_refusesAPaidFicha() {
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.paidCharge(CHARGE_ID)));

		assertThatThrownBy(() -> useCase.preview(candidateId)).isInstanceOf(CandidateAlreadyPaidException.class);
	}

	@Test
	void preview_ofADifference_isWhatIsLeftToPay() {
		UUID difference = UUID.randomUUID();
		ReflectionTestUtils.setField(candidate, "programChangePaymentId", difference);
		when(fichaPaymentPort.findProgramChangeCharge(difference)).thenReturn(Optional.of(
				new FichaPaymentPort.ProgramChangeCharge(difference, "REF-C", new BigDecimal("800.00"), PRICE,
						new BigDecimal("300.00"), NOW.plusSeconds(86400), false, false, null, null)));

		var preview = useCase.preview(candidateId);

		assertThat(preview.programChange()).isTrue();
		assertThat(preview.total()).isEqualByComparingTo("800.00");
		assertThat(preview.alreadyPaid()).isEqualByComparingTo("500.00");
		assertThat(preview.toCollect()).isEqualByComparingTo("300.00");
		verify(admissionQuotaPort, never()).countOccupiedExcluding(any(), any(), any());
	}

	@Test
	void collect_checksAgainAndBooksWithTheCollectorsName() {
		stubOwedFicha(PRICE, 0);
		UUID cashier = UUID.randomUUID();
		when(staffDirectory.displayName(cashier)).thenReturn("Ana Caja");

		useCase.collect(candidateId, PRICE, cashier);

		verify(confirmAdmissionPaymentUseCase).confirmCash(candidateId, PRICE, cashier, "Ana Caja");
	}

	@Test
	void collect_bookNothingWhenACheckFails() {
		stubOwedFicha(PRICE, 10);

		assertThatThrownBy(() -> useCase.collect(candidateId, PRICE, UUID.randomUUID()))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class);
		verify(confirmAdmissionPaymentUseCase, never()).confirmCash(any(), any(), any(), any());
	}
}
