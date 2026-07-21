package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Updates an existing {@code AcademicPeriod}'s catalog fields (PUT
 * {@code /periods/{id}}). {@code (year, periodNumber)} uniqueness is
 * revalidated, EXCLUDING the record's own current row — same self-update
 * rule as {@code UpdateSubjectClassificationUseCase}'s {@code code}
 * revalidation. {@code status} is deliberately absent — status transitions
 * are the sole responsibility of {@code ChangeAcademicPeriodStatusUseCase}.
 */
public interface UpdateAcademicPeriodUseCase {

	PeriodResult updatePeriod(UpdatePeriodCommand command);

	record UpdatePeriodCommand(UUID periodId, String name, int year, int periodNumber, PeriodType type,
			LocalDate startDate, LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
	}
}
