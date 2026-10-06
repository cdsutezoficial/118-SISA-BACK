package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for {@link PaymentRateRepositoryAdapter}, the three queries
 * the reconciliation and the editor both read through.
 *
 * <p>
 * The two halves of this class used to be about dates — a row was "in force" when
 * a date fell inside its range. That is now a status, so "in force" is
 * {@code status = ACTIVE} and the date is only ever {@code createdAt}, an audit
 * field. The null-handling assertions are the ones worth keeping here: a rate is
 * addressed by the whole {@code (program, level, period)} triple with nulls
 * meaning real values rather than wildcards, because {@code null} is a rung of the
 * pricing ladder in its own right, not "any program".
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * {@code ddl-auto=create-drop} means whatever database {@code DB_URL} names is
 * dropped and rebuilt from the entities. Point {@code DB_URL} at a throwaway
 * database when running this locally.
 */
@DataJpaTest
@Import(PaymentRateRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentRateRepositoryAdapterIT {

	private static final LocalDateTime EARLY = LocalDateTime.of(2025, 1, 1, 8, 0);

	private static final LocalDateTime LATE = LocalDateTime.of(2026, 1, 1, 8, 0);

	@Autowired
	private PaymentRateRepositoryAdapter adapter;

	@Autowired
	private PaymentRateJpaRepository jpaRepository;

	@Test
	void saveInsertsANewRow() {
		PaymentRate saved = adapter.save(newRate(UUID.randomUUID(), UUID.randomUUID(), AcademicLevel.LICENCIATURA,
				null, LATE));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getStatus()).isEqualTo(PaymentRateStatus.ACTIVE);
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findActiveMatchesTheExactCombinationTreatingNullAsItsOwnValue() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, programId, AcademicLevel.LICENCIATURA, null, LATE));
		jpaRepository.save(newRate(conceptId, null, null, null, LATE));

		Optional<PaymentRate> found = adapter.findActive(conceptId, programId, AcademicLevel.LICENCIATURA, null);
		Optional<PaymentRate> foundWithNulls = adapter.findActive(conceptId, null, null, null);

		assertThat(found).isPresent();
		assertThat(found.get().getProgramId()).isEqualTo(programId);
		assertThat(foundWithNulls).isPresent();
		assertThat(foundWithNulls.get().getProgramId()).isNull();
	}

	@Test
	void findActiveExcludesRowsAlreadyDeactivated() {
		UUID conceptId = UUID.randomUUID();
		PaymentRate closed = newRate(conceptId, null, null, null, EARLY);
		closed.deactivate();
		jpaRepository.save(closed);

		assertThat(adapter.findActive(conceptId, null, null, null)).isEmpty();
	}

	@Test
	void findActiveFindsTheReplacementAfterADeactivation() {
		UUID conceptId = UUID.randomUUID();
		PaymentRate previous = newRate(conceptId, null, null, null, EARLY);
		previous.deactivate();
		jpaRepository.save(previous);
		PaymentRate current = jpaRepository.save(newRate(conceptId, null, null, null, LATE));

		Optional<PaymentRate> found = adapter.findActive(conceptId, null, null, null);

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(current.getId());
		assertThat(found.get().getAmount()).isEqualTo(current.getAmount());
	}

	@Test
	void findActiveMatchesTheSamePeriodOnly() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, programId, AcademicLevel.TSU, periodId, LATE));

		assertThat(adapter.findActive(conceptId, programId, AcademicLevel.TSU, periodId)).isPresent();
		assertThat(adapter.findActive(conceptId, programId, AcademicLevel.TSU, UUID.randomUUID())).isEmpty();
	}

	@Test
	void findActiveTreatsNullProgramAndLevelAsTheirOwnValue() {
		UUID conceptId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, null, null, periodId, LATE));

		assertThat(adapter.findActive(conceptId, null, null, periodId)).isPresent();
		assertThat(adapter.findActive(conceptId, UUID.randomUUID(), null, periodId)).isEmpty();
	}

	@Test
	void findActiveIsScopedToItsOwnConcept() {
		UUID conceptId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, null, null, null, LATE));

		assertThat(adapter.findActive(UUID.randomUUID(), null, null, null)).isEmpty();
	}

	@Test
	void findHistoryByConceptIdReturnsOnlyThatConceptsRowsNewestFirst() {
		UUID conceptId = UUID.randomUUID();
		UUID otherConceptId = UUID.randomUUID();
		jpaRepository.save(newRate(conceptId, null, null, null, EARLY));
		jpaRepository.save(newRate(conceptId, null, null, null, LATE));
		jpaRepository.save(newRate(otherConceptId, null, null, null, LATE));

		List<PaymentRate> history = adapter.findHistoryByConceptId(conceptId);

		assertThat(history).hasSize(2);
		assertThat(history.get(0).getCreatedAt()).isEqualTo(LATE);
		assertThat(history.get(1).getCreatedAt()).isEqualTo(EARLY);
	}

	/**
	 * History is the whole reason rates are never deleted, so the reader has to
	 * return the INACTIVE rows as readily as the ACTIVE one. This used to be a
	 * by-product of the date range; now it is the point.
	 */
	@Test
	void findHistoryByConceptIdKeepsDeactivatedRows() {
		UUID conceptId = UUID.randomUUID();
		PaymentRate previous = newRate(conceptId, null, null, null, EARLY);
		previous.deactivate();
		jpaRepository.save(previous);
		jpaRepository.save(newRate(conceptId, null, null, null, LATE));

		assertThat(adapter.findHistoryByConceptId(conceptId)).extracting(PaymentRate::getStatus)
				.containsExactly(PaymentRateStatus.ACTIVE, PaymentRateStatus.INACTIVE);
	}

	@Test
	void findHistoryByConceptIdReturnsEmptyForUnknownConcept() {
		assertThat(adapter.findHistoryByConceptId(UUID.randomUUID())).isEmpty();
	}

	private static PaymentRate newRate(UUID conceptId, UUID programId, AcademicLevel level, UUID periodId,
			LocalDateTime createdAt) {
		return new PaymentRate(conceptId, programId, level, BigDecimal.valueOf(1000), periodId, createdAt);
	}
}