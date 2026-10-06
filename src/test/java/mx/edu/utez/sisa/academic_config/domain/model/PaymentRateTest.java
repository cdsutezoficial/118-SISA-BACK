package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRateTest {

	@Test
	void constructor_setsAllFields() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 8, 0);

		PaymentRate rate = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(1500),
				periodId, createdAt);

		assertThat(rate.getConceptId()).isEqualTo(conceptId);
		assertThat(rate.getProgramId()).isEqualTo(programId);
		assertThat(rate.getLevel()).isEqualTo(AcademicLevel.LICENCIATURA);
		assertThat(rate.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(rate.getPeriodId()).isEqualTo(periodId);
		assertThat(rate.getCreatedAt()).isEqualTo(createdAt);
	}

	/**
	 * A new row is the one in force. It has to be, or the reconciliation's
	 * "unchanged amount keeps the existing row" branch could never find one and
	 * every save would write history for a price that did not change.
	 */
	@Test
	void constructor_startsActive() {
		PaymentRate rate = newContinuousRate();

		assertThat(rate.getStatus()).isEqualTo(PaymentRateStatus.ACTIVE);
	}

	@Test
	void deactivate_marksTheRowInactiveWithoutTouchingItsAmountOrCreatedAt() {
		PaymentRate rate = newContinuousRate();
		LocalDateTime createdAt = rate.getCreatedAt();

		rate.deactivate();

		assertThat(rate.getStatus()).isEqualTo(PaymentRateStatus.INACTIVE);
		assertThat(rate.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(rate.getCreatedAt()).isEqualTo(createdAt);
	}

	/**
	 * Deactivating twice is idempotent on purpose: {@code reconcileRates}
	 * deactivates the superseded row and then, if the same destination appears in
	 * the retire pass, would otherwise close an already-closed row again. Nothing
	 * in the catalog can tell the difference, so the method stays a plain setter.
	 */
	@Test
	void deactivate_isIdempotent() {
		PaymentRate rate = newContinuousRate();

		rate.deactivate();
		rate.deactivate();

		assertThat(rate.getStatus()).isEqualTo(PaymentRateStatus.INACTIVE);
	}

	@Test
	void constructor_allowsNullProgramIdLevelAndPeriodId() {
		PaymentRate rate = new PaymentRate(UUID.randomUUID(), null, null, BigDecimal.valueOf(500), null,
				LocalDateTime.of(2026, 1, 1, 8, 0));

		assertThat(rate.getProgramId()).isNull();
		assertThat(rate.getLevel()).isNull();
		assertThat(rate.getPeriodId()).isNull();
	}

	private static PaymentRate newContinuousRate() {
		return new PaymentRate(UUID.randomUUID(), UUID.randomUUID(), AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(1500), null, LocalDateTime.of(2026, 1, 1, 8, 0));
	}
}