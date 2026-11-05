package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicDivisionJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPeriodJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.ProgramAdmissionConfigJpaRepository;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort.PeriodRef;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionDashboardQueryPort.StatusCount;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas del tablero de Admisión sobre una base real: el JPQL (los joins
 * entre candidato, configuración, carrera y periodo, el {@code GROUP BY} y el
 * orden del periodo más reciente) solo lo valida Hibernate al ejecutarlo.
 *
 * <h2>Aviso: esta clase recrea el esquema al que apunta</h2>
 * Correrla solo con {@code DB_URL} apuntando a {@code sisa_it}.
 */
@DataJpaTest
@Import(AdmissionDashboardQueryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AdmissionDashboardQueryAdapterIT {

	@Autowired
	private AdmissionDashboardQueryAdapter adapter;

	@Autowired
	private CandidateJpaRepository candidateJpaRepository;

	@Autowired
	private AcademicDivisionJpaRepository divisionRepository;

	@Autowired
	private AcademicProgramJpaRepository programRepository;

	@Autowired
	private AcademicPeriodJpaRepository periodRepository;

	@Autowired
	private ProgramAdmissionConfigJpaRepository configRepository;

	private final AtomicInteger seq = new AtomicInteger();

	@Test
	void countsByProgramStatusAndInductionWithinThePeriodOnly() {
		UUID division = newDivision();
		UUID period = newPeriod();
		Seeds software = newConfig(division, period, "2026-01-01T00:00:00Z");
		Seeds industrial = newConfig(division, period, "2026-01-01T00:00:00Z");
		Seeds otherPeriod = newConfig(division, newPeriod(), "2026-01-01T00:00:00Z");
		candidate(software.configId(), CandidateStatus.REGISTERED, false);
		candidate(software.configId(), CandidateStatus.PAID, false);
		candidate(software.configId(), CandidateStatus.PAID, true);
		candidate(industrial.configId(), CandidateStatus.ACCEPTED, true);
		candidate(otherPeriod.configId(), CandidateStatus.PAID, false);

		assertThat(adapter.countByProgramAndStatus(period, null))
				.extracting(StatusCount::programId, StatusCount::status, StatusCount::inductionEnabled,
						StatusCount::count)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple(software.programId(), CandidateStatus.REGISTERED, false, 1L),
						org.assertj.core.groups.Tuple.tuple(software.programId(), CandidateStatus.PAID, false, 1L),
						org.assertj.core.groups.Tuple.tuple(software.programId(), CandidateStatus.PAID, true, 1L),
						org.assertj.core.groups.Tuple.tuple(industrial.programId(), CandidateStatus.ACCEPTED, true, 1L));
		assertThat(adapter.countByProgramAndStatus(period, null)).allSatisfy(row -> {
			assertThat(row.programName()).startsWith("Carrera ");
			assertThat(row.modality()).isEqualTo(ProgramModality.PRESENCIAL);
		});
	}

	@Test
	void aDivisionOnlySeesItsPrograms() {
		UUID period = newPeriod();
		Seeds mine = newConfig(newDivision(), period, "2026-01-01T00:00:00Z");
		Seeds theirs = newConfig(newDivision(), period, "2026-01-01T00:00:00Z");
		candidate(mine.configId(), CandidateStatus.REGISTERED, false);
		candidate(theirs.configId(), CandidateStatus.REGISTERED, false);
		UUID myDivision = programRepository.findById(mine.programId()).orElseThrow().getDivisionId();

		assertThat(adapter.countByProgramAndStatus(period, myDivision)).extracting(StatusCount::programId)
				.containsExactly(mine.programId());
	}

	@Test
	void theLatestPeriodPrefersAnOpenConfigThenTheLastOneThatOpened() {
		UUID division = newDivision();
		UUID older = newPeriod();
		UUID newer = newPeriod();
		newConfig(division, older, "2026-01-01T00:00:00Z");
		Seeds newest = newConfig(division, newer, "2026-06-01T00:00:00Z");

		assertThat(adapter.findLatestAdmissionPeriod()).map(PeriodRef::id).contains(newer);

		ProgramAdmissionConfig closed = configRepository.findById(newest.configId()).orElseThrow();
		ReflectionTestUtils.setField(closed, "status", ProgramAdmissionConfigStatus.CLOSED);
		configRepository.save(closed);

		assertThat(adapter.findLatestAdmissionPeriod()).map(PeriodRef::id).contains(older);
		assertThat(adapter.findPeriod(older)).map(PeriodRef::name).hasValueSatisfying(
				name -> assertThat(name).startsWith("Periodo "));
	}

	private UUID newDivision() {
		int n = seq.incrementAndGet();
		return divisionRepository.save(new AcademicDivision("Division " + n, "DIV" + n, "d", null)).getId();
	}

	private UUID newPeriod() {
		int n = seq.incrementAndGet();
		return periodRepository.save(new AcademicPeriod("Periodo " + n, 2026, n, PeriodType.CUATRIMESTRAL,
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 4, 30), LocalDate.of(2025, 11, 1),
				LocalDate.of(2025, 12, 15))).getId();
	}

	private Seeds newConfig(UUID divisionId, UUID periodId, String opensAt) {
		int n = seq.incrementAndGet();
		AcademicProgram program = programRepository.save(new AcademicProgram(divisionId, "Carrera " + n,
				"Oferta " + n, "PROG" + n, AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, null, null));
		ProgramAdmissionConfig config = configRepository.save(new ProgramAdmissionConfig(program.getId(), periodId,
				UUID.randomUUID(), true, 50, Instant.parse(opensAt), Instant.parse("2026-12-31T00:00:00Z")));
		return new Seeds(program.getId(), config.getId());
	}

	private void candidate(UUID configId, CandidateStatus status, boolean induction) {
		int n = seq.incrementAndGet();
		Candidate candidate = new Candidate(configId, "ADM-2026-" + n, true, true, null);
		candidate.setCurp(String.format("CURP%014d", n));
		candidate.setFirstName("Nombre" + n);
		candidate.setLastName1("Apellido" + n);
		ReflectionTestUtils.setField(candidate, "status", status);
		ReflectionTestUtils.setField(candidate, "isEnabledForInduction", induction);
		candidateJpaRepository.save(candidate);
	}

	private record Seeds(UUID programId, UUID configId) {
	}
}
