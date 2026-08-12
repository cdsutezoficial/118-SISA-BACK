package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link PaymentRateRepositoryAdapter}, mirroring
 * {@code PaymentConceptRepositoryAdapterSearchIT}'s style. Exercises the
 * null-safe combination-matching JPQL in {@link PaymentRateJpaRepository}
 * directly against H2, since that logic cannot be verified by a mocked unit
 * test.
 */
@DataJpaTest
@Import(PaymentRateRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentRateRepositoryAdapterIT {

	@Autowired
	private PaymentRateRepositoryAdapter adapter;

	@Autowired
	private PaymentRateJpaRepository jpaRepository;

	@Test
	void saveInsertsANewRow() {
		PaymentRate saved = adapter.save(newRate(UUID.randomUUID(), UUID.randomUUID(), AcademicLevel.LICENCIATURA,
				null, LocalDate.of(2026, 1, 1)));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findActiveContinuousRateMatchesTheExactCombinationTreatingNullAsItsOwnValue() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, programId, AcademicLevel.LICENCIATURA, null, LocalDate.of(2026, 1, 1)));
		jpaRepository.save(newRate(conceptId, null, null, null, LocalDate.of(2026, 1, 1)));

		Optional<PaymentRate> found = adapter.findActiveContinuousRate(conceptId, programId,
				AcademicLevel.LICENCIATURA);
		Optional<PaymentRate> foundWithNulls = adapter.findActiveContinuousRate(conceptId, null, null);

		assertThat(found).isPresent();
		assertThat(found.get().getProgramId()).isEqualTo(programId);
		assertThat(foundWithNulls).isPresent();
		assertThat(foundWithNulls.get().getProgramId()).isNull();
	}

	@Test
	void findActiveContinuousRateExcludesRowsAlreadyClosed() {
		UUID conceptId = UUID.randomUUID();
		PaymentRate closed = newRate(conceptId, null, null, null, LocalDate.of(2025, 1, 1));
		closed.close(LocalDate.of(2026, 1, 1));
		jpaRepository.save(closed);

		Optional<PaymentRate> found = adapter.findActiveContinuousRate(conceptId, null, null);

		assertThat(found).isEmpty();
	}

	@Test
	void findActiveContinuousRateExcludesPeriodScopedRows() {
		UUID conceptId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, null, null, UUID.randomUUID(), LocalDate.of(2026, 1, 1)));

		Optional<PaymentRate> found = adapter.findActiveContinuousRate(conceptId, null, null);

		assertThat(found).isEmpty();
	}

	@Test
	void existsByExactCombinationMatchesSamePeriodOnly() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID otherPeriodId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, programId, AcademicLevel.TSU, periodId, LocalDate.of(2026, 1, 1)));

		boolean samePeriod = adapter.existsByExactCombination(conceptId, programId, AcademicLevel.TSU, periodId);
		boolean otherPeriod = adapter.existsByExactCombination(conceptId, programId, AcademicLevel.TSU,
				otherPeriodId);

		assertThat(samePeriod).isTrue();
		assertThat(otherPeriod).isFalse();
	}

	@Test
	void existsByExactCombinationTreatsNullProgramAndLevelAsTheirOwnValue() {
		UUID conceptId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, null, null, periodId, LocalDate.of(2026, 1, 1)));

		boolean matchesNulls = adapter.existsByExactCombination(conceptId, null, null, periodId);
		boolean doesNotMatchNonNullProgram = adapter.existsByExactCombination(conceptId, UUID.randomUUID(), null,
				periodId);

		assertThat(matchesNulls).isTrue();
		assertThat(doesNotMatchNonNullProgram).isFalse();
	}

	@Test
	void findHistoryByConceptIdReturnsOnlyThatConceptsRowsOrderedByValidFromDescending() {
		UUID conceptId = UUID.randomUUID();
		UUID otherConceptId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, null, null, null, LocalDate.of(2025, 1, 1)));
		jpaRepository.save(newRate(conceptId, null, null, null, LocalDate.of(2026, 1, 1)));
		jpaRepository.save(newRate(otherConceptId, null, null, null, LocalDate.of(2026, 1, 1)));

		List<PaymentRate> history = adapter.findHistoryByConceptId(conceptId);

		assertThat(history).hasSize(2);
		assertThat(history.get(0).getValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(history.get(1).getValidFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
	}

	@Test
	void findHistoryByConceptIdReturnsEmptyForUnknownConcept() {
		assertThat(adapter.findHistoryByConceptId(UUID.randomUUID())).isEmpty();
	}

	private static PaymentRate newRate(UUID conceptId, UUID programId, AcademicLevel level, UUID periodId,
			LocalDate validFrom) {
		return new PaymentRate(conceptId, programId, level, BigDecimal.valueOf(1000), periodId, validFrom);
	}
}
