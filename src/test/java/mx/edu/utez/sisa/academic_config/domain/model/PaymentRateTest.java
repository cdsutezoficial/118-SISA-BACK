package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRateTest {

	@Test
	void constructor_setsAllFields() {
		UUID conceptId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		LocalDate validFrom = LocalDate.of(2026, 1, 1);

		PaymentRate rate = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(1500),
				periodId, validFrom);

		assertThat(rate.getConceptId()).isEqualTo(conceptId);
		assertThat(rate.getProgramId()).isEqualTo(programId);
		assertThat(rate.getLevel()).isEqualTo(AcademicLevel.LICENCIATURA);
		assertThat(rate.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(rate.getPeriodId()).isEqualTo(periodId);
		assertThat(rate.getValidFrom()).isEqualTo(validFrom);
	}

	@Test
	void constructor_defaultsValidToToNull() {
		PaymentRate rate = newContinuousRate();

		assertThat(rate.getValidTo()).isNull();
	}

	@Test
	void constructor_allowsNullProgramIdLevelAndPeriodId() {
		PaymentRate rate = new PaymentRate(UUID.randomUUID(), null, null, BigDecimal.valueOf(500), null,
				LocalDate.of(2026, 1, 1));

		assertThat(rate.getProgramId()).isNull();
		assertThat(rate.getLevel()).isNull();
		assertThat(rate.getPeriodId()).isNull();
	}

	@Test
	void close_setsValidToToTheDayBeforeTheNewValidFrom() {
		PaymentRate rate = newContinuousRate();

		rate.close(LocalDate.of(2026, 6, 1));

		assertThat(rate.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
	}

	private static PaymentRate newContinuousRate() {
		return new PaymentRate(UUID.randomUUID(), UUID.randomUUID(), AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(1500), null, LocalDate.of(2026, 1, 1));
	}
}
