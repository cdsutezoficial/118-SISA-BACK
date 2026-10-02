package mx.edu.utez.sisa.academic_config.domain.service;

final class AcademicProgramTextNormalizer {

	private AcademicProgramTextNormalizer() {
	}

	static String name(String value) {
		return value.trim();
	}

	static String offerName(String value) {
		return value.trim();
	}

	static String code(String value) {
		return value.trim().toUpperCase(java.util.Locale.ROOT);
	}

	static String optional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}