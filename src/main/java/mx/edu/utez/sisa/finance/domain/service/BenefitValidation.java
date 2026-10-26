package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.shared.exception.InvalidBenefitDataException;

import java.time.LocalDate;

/**
 * The one place the discount window rule lives, shared by create and update:
 * {@code validFrom}/{@code validTo} are mandatory (Fase 2, pregunta 5) and the
 * window cannot run backwards.
 */
final class BenefitValidation {

	private BenefitValidation() {
	}

	static void validateWindow(LocalDate validFrom, LocalDate validTo) {
		if (validFrom == null) {
			throw new InvalidBenefitDataException("validFrom is required");
		}
		if (validTo == null) {
			throw new InvalidBenefitDataException("validTo is required");
		}
		if (validTo.isBefore(validFrom)) {
			throw new InvalidBenefitDataException("validTo must not be before validFrom");
		}
	}
}
