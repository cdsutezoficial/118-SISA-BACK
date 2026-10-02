package mx.edu.utez.sisa.academic_config.domain.service;

final class AcademicDivisionTextNormalizer {

	private AcademicDivisionTextNormalizer() {
	}

	static String name(String value) {
		return value.trim();
	}

	static String code(String value) {
		return value.trim().toUpperCase(java.util.Locale.ROOT);
	}

	static String description(String value) {
		return value == null ? null : value.trim();
	}
}