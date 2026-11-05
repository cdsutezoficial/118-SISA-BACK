package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetAdmissionDashboardUseCase.AdmissionDashboard;
import mx.edu.utez.sisa.admission.domain.port.in.GetAdmissionDashboardUseCase.Funnel;
import mx.edu.utez.sisa.admission.domain.port.in.GetAdmissionDashboardUseCase.ProgramStats;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort.PeriodRef;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort.StatusCount;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort.DivisionScope;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdmissionDashboardUseCaseImplTest {

	private static final UUID CALLER = UUID.randomUUID();
	private static final UUID PERIOD = UUID.randomUUID();
	private static final UUID SOFTWARE = UUID.randomUUID();
	private static final UUID INDUSTRIAL = UUID.randomUUID();

	@Mock
	private AdmissionDashboardQueryPort queryPort;

	@Mock
	private CallerDivisionScopePort scopePort;

	private GetAdmissionDashboardUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new GetAdmissionDashboardUseCaseImpl(queryPort, scopePort);
	}

	@Test
	void eachFunnelStageCountsWhoReachedItOrWentPast() {
		when(queryPort.findLatestAdmissionPeriod()).thenReturn(Optional.of(new PeriodRef(PERIOD, "Sep–Dic 2026")));
		when(scopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(queryPort.countByProgramAndStatus(PERIOD, null)).thenReturn(List.of(
				row(SOFTWARE, "Software", CandidateStatus.REGISTERED, false, 4),
				row(SOFTWARE, "Software", CandidateStatus.PAYMENT_EXPIRED, false, 1),
				row(SOFTWARE, "Software", CandidateStatus.PAID, false, 3),
				row(SOFTWARE, "Software", CandidateStatus.EXAM_TAKEN, true, 2),
				row(INDUSTRIAL, "Industrial", CandidateStatus.ACCEPTED, true, 2),
				row(INDUSTRIAL, "Industrial", CandidateStatus.REJECTED, false, 1),
				row(INDUSTRIAL, "Industrial", CandidateStatus.ENROLLED, true, 1)));

		AdmissionDashboard dashboard = useCase.get(CALLER, null);

		assertThat(dashboard.periodId()).isEqualTo(PERIOD);
		assertThat(dashboard.periodName()).isEqualTo("Sep–Dic 2026");
		assertThat(dashboard.funnel()).isEqualTo(new Funnel(14, 9, 6, 5, 3, 1));
		assertThat(dashboard.programs()).containsExactly(
				new ProgramStats(SOFTWARE, "Software", ProgramModality.PRESENCIAL, 10, 5, 0, 9, 0, 1),
				new ProgramStats(INDUSTRIAL, "Industrial", ProgramModality.PRESENCIAL, 4, 4, 3, 2, 2, 0));
	}

	@Test
	void aRequestedPeriodIsUsedAndADirectorOnlyCountsTheirDivision() {
		UUID division = UUID.randomUUID();
		when(queryPort.findPeriod(PERIOD)).thenReturn(Optional.of(new PeriodRef(PERIOD, "Ene–Abr 2027")));
		when(scopePort.resolve(CALLER)).thenReturn(DivisionScope.director(division));
		when(queryPort.countByProgramAndStatus(PERIOD, division)).thenReturn(List.of());

		AdmissionDashboard dashboard = useCase.get(CALLER, PERIOD);

		assertThat(dashboard.periodName()).isEqualTo("Ene–Abr 2027");
		assertThat(dashboard.programs()).isEmpty();
		verify(queryPort, never()).findLatestAdmissionPeriod();
	}

	@Test
	void aDirectorWithoutADivisionSeesNothing() {
		when(queryPort.findLatestAdmissionPeriod()).thenReturn(Optional.of(new PeriodRef(PERIOD, "Sep–Dic 2026")));
		when(scopePort.resolve(CALLER)).thenReturn(DivisionScope.director(null));

		AdmissionDashboard dashboard = useCase.get(CALLER, null);

		assertThat(dashboard.funnel().registered()).isZero();
		verify(queryPort, never()).countByProgramAndStatus(any(), any());
	}

	@Test
	void withoutAnyAdmissionProcessTheDashboardIsEmpty() {
		when(queryPort.findLatestAdmissionPeriod()).thenReturn(Optional.empty());

		AdmissionDashboard dashboard = useCase.get(CALLER, null);

		assertThat(dashboard.periodId()).isNull();
		assertThat(dashboard.funnel()).isEqualTo(new Funnel(0, 0, 0, 0, 0, 0));
		verify(queryPort, never()).countByProgramAndStatus(any(), any());
	}

	private static StatusCount row(UUID programId, String name, CandidateStatus status, boolean induction,
			long count) {
		return new StatusCount(programId, name, ProgramModality.PRESENCIAL, status, induction, count);
	}
}
