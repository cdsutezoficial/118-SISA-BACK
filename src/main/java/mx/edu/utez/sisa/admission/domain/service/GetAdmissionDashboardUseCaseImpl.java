package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetAdmissionDashboardUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort.PeriodRef;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort.StatusCount;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GetAdmissionDashboardUseCaseImpl implements GetAdmissionDashboardUseCase {

	private static final Set<CandidateStatus> PAID = EnumSet.of(CandidateStatus.PAID, CandidateStatus.EXAM_TAKEN,
			CandidateStatus.ACCEPTED, CandidateStatus.REJECTED, CandidateStatus.ENROLLED);

	private static final Set<CandidateStatus> EXAM_TAKEN = EnumSet.of(CandidateStatus.EXAM_TAKEN,
			CandidateStatus.ACCEPTED, CandidateStatus.REJECTED, CandidateStatus.ENROLLED);

	private static final Set<CandidateStatus> ADMITTED = EnumSet.of(CandidateStatus.ACCEPTED,
			CandidateStatus.ENROLLED);

	private static final Set<CandidateStatus> COMPLETED = EnumSet.of(CandidateStatus.ENROLLED,
			CandidateStatus.REJECTED);

	private final AdmissionDashboardQueryPort queryPort;

	private final CallerDivisionScopePort callerDivisionScopePort;

	public GetAdmissionDashboardUseCaseImpl(AdmissionDashboardQueryPort queryPort,
			CallerDivisionScopePort callerDivisionScopePort) {
		this.queryPort = queryPort;
		this.callerDivisionScopePort = callerDivisionScopePort;
	}

	@Override
	public AdmissionDashboard get(UUID callerId, UUID periodId) {
		PeriodRef period = (periodId != null ? queryPort.findPeriod(periodId) : queryPort.findLatestAdmissionPeriod())
				.orElse(null);
		if (period == null) {
			return empty(periodId, null);
		}
		CallerDivisionScopePort.DivisionScope scope = callerDivisionScopePort.resolve(callerId);
		if (scope.divisionScoped() && scope.divisionId() == null) {
			// Igual que la lista: un Director sin división no ve nada.
			return empty(period.id(), period.name());
		}
		List<StatusCount> counts = queryPort.countByProgramAndStatus(period.id(),
				scope.divisionScoped() ? scope.divisionId() : null);
		return new AdmissionDashboard(period.id(), period.name(), funnel(counts), programs(counts));
	}

	private static Funnel funnel(List<StatusCount> counts) {
		long registered = 0, paid = 0, exam = 0, induction = 0, admitted = 0, expired = 0;
		for (StatusCount c : counts) {
			registered += c.count();
			if (PAID.contains(c.status())) {
				paid += c.count();
			}
			if (EXAM_TAKEN.contains(c.status())) {
				exam += c.count();
			}
			if (c.inductionEnabled()) {
				induction += c.count();
			}
			if (ADMITTED.contains(c.status())) {
				admitted += c.count();
			}
			if (c.status() == CandidateStatus.PAYMENT_EXPIRED) {
				expired += c.count();
			}
		}
		return new Funnel(registered, paid, exam, induction, admitted, expired);
	}

	private static List<ProgramStats> programs(List<StatusCount> counts) {
		Map<UUID, long[]> totals = new LinkedHashMap<>();
		Map<UUID, StatusCount> names = new LinkedHashMap<>();
		for (StatusCount c : counts) {
			names.putIfAbsent(c.programId(), c);
			long[] t = totals.computeIfAbsent(c.programId(), id -> new long[6]);
			t[0] += c.count();
			if (PAID.contains(c.status())) {
				t[1] += c.count();
			}
			if (ADMITTED.contains(c.status())) {
				t[2] += c.count();
			}
			if (c.status() == CandidateStatus.PAYMENT_EXPIRED) {
				t[5] += c.count();
			} else if (COMPLETED.contains(c.status())) {
				t[4] += c.count();
			} else {
				t[3] += c.count();
			}
		}
		List<ProgramStats> programs = new ArrayList<>();
		totals.forEach((programId, t) -> {
			StatusCount ref = names.get(programId);
			programs.add(new ProgramStats(programId, ref.programName(), ref.modality(), t[0], t[1], t[2], t[3], t[4],
					t[5]));
		});
		programs.sort(Comparator.comparingLong(ProgramStats::registered).reversed()
				.thenComparing(ProgramStats::programName, Comparator.nullsLast(Comparator.naturalOrder())));
		return programs;
	}

	private static AdmissionDashboard empty(UUID periodId, String periodName) {
		return new AdmissionDashboard(periodId, periodName, new Funnel(0, 0, 0, 0, 0, 0), List.of());
	}
}
